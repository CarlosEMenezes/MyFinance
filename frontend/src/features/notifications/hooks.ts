import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { Notification, NotificationSettings } from '../../types/api';

import {
  fetchNotificationSettings,
  fetchNotifications,
  markNotificationsRead,
  updateNotificationSettings,
} from './api';

export const notificationsQueryKey = ['notifications'] as const;
export const notificationSettingsQueryKey = ['notifications', 'settings'] as const;

/** BR-12: the lead times that may be chosen, widest first. */
export const LEAD_DAY_OPTIONS = [10, 5, 2] as const;

export type ChannelKey = keyof NotificationSettings['channels'];

export interface LeadTimeView {
  readonly days: number;
  readonly enabled: boolean;
  /** How many items in the whole queue this lead time catches. */
  readonly itemCount: number;
}

export interface NotificationsView {
  /** What BR-12 says to show, in the order the server sorted it. */
  readonly visible: readonly Notification[];
  readonly unreadCount: number;
  readonly leadTimes: readonly LeadTimeView[];
  readonly channels: NotificationSettings['channels'];
  readonly setLeadEnabled: (days: number, enabled: boolean) => void;
  readonly setChannel: (channel: ChannelKey, on: boolean) => void;
  readonly setRead: (key: string, read: boolean) => void;
  readonly markAllRead: () => void;
  readonly isLoading: boolean;
  readonly error: Error | null;
}

/**
 * BR-12: shown when `daysUntilDue <= max(enabled lead days)`.
 *
 * With no lead time enabled the widest is zero, which still admits anything
 * due today or already overdue — turning every warning off must not hide money
 * that has already left.
 */
export function widestLead(leadDays: readonly number[]): number {
  return leadDays.length === 0 ? 0 : Math.max(...leadDays);
}

/**
 * What a lead time currently catches. Counted over the whole queue rather than
 * the visible part, because the number exists to say what turning the toggle
 * *on* would add — counting only what is already shown would make every
 * enabled option report itself.
 *
 * Overdue items are excluded: they are past every lead time, so attributing
 * them to one would overstate it.
 */
function countCaught(queue: readonly Notification[], days: number): number {
  return queue.filter((item) => item.daysUntilDue <= days && item.daysUntilDue >= 0).length;
}

export function useNotifications(): NotificationsView {
  const queryClient = useQueryClient();

  const queue = useQuery({ queryKey: notificationsQueryKey, queryFn: fetchNotifications });
  const settings = useQuery({
    queryKey: notificationSettingsQueryKey,
    queryFn: fetchNotificationSettings,
  });

  const items = queue.data ?? [];
  const leadDays = settings.data?.leadDays ?? [];
  const channels = settings.data?.channels ?? { push: false, email: false, weeklySummary: false };

  const visible = items.filter((item) => item.daysUntilDue <= widestLead(leadDays));

  const readMutation = useMutation({
    mutationFn: (variables: { keys: readonly string[]; read: boolean }) =>
      markNotificationsRead(variables),

    onMutate: async ({ keys, read }) => {
      await queryClient.cancelQueries({ queryKey: notificationsQueryKey });
      const previous = queryClient.getQueryData<readonly Notification[]>(notificationsQueryKey);

      queryClient.setQueryData<readonly Notification[]>(notificationsQueryKey, (current) =>
        (current ?? []).map((item) =>
          keys.includes(item.key)
            ? { ...item, readAt: read ? new Date().toISOString() : null }
            : item,
        ),
      );

      return { previous };
    },

    onError: (_error, _variables, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(notificationsQueryKey, context.previous);
      }
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: notificationsQueryKey });
    },
  });

  const settingsMutation = useMutation({
    mutationFn: updateNotificationSettings,

    onMutate: async (changes) => {
      await queryClient.cancelQueries({ queryKey: notificationSettingsQueryKey });
      const previous = queryClient.getQueryData<NotificationSettings>(notificationSettingsQueryKey);

      if (previous !== undefined) {
        queryClient.setQueryData<NotificationSettings>(notificationSettingsQueryKey, {
          leadDays: changes.leadDays ?? previous.leadDays,
          channels: changes.channels ?? previous.channels,
        });
      }

      return { previous };
    },

    onError: (_error, _variables, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(notificationSettingsQueryKey, context.previous);
      }
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: notificationSettingsQueryKey });
    },
  });

  return {
    visible,
    unreadCount: visible.filter((item) => item.readAt === null).length,

    leadTimes: LEAD_DAY_OPTIONS.map((days) => ({
      days,
      enabled: leadDays.includes(days),
      itemCount: countCaught(items, days),
    })),

    channels,

    setLeadEnabled: (days, enabled) => {
      const next = enabled
        ? [...leadDays, days].sort((a, b) => b - a)
        : leadDays.filter((day) => day !== days);
      settingsMutation.mutate({ leadDays: next });
    },

    setChannel: (channel, on) => {
      settingsMutation.mutate({ channels: { ...channels, [channel]: on } });
    },

    setRead: (key, read) => {
      readMutation.mutate({ keys: [key], read });
    },

    markAllRead: () => {
      const unread = visible.filter((item) => item.readAt === null).map((item) => item.key);
      if (unread.length > 0) {
        readMutation.mutate({ keys: unread, read: true });
      }
    },

    isLoading: queue.isPending || settings.isPending,
    error: queue.error ?? settings.error,
  };
}
