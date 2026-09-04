import { getJson, patchJson } from '../../lib/http';
import type {
  MarkNotificationsReadRequest,
  Notification,
  NotificationSettings,
  UpdateNotificationSettingsRequest,
} from '../../types/api';

export function fetchNotifications(): Promise<readonly Notification[]> {
  return getJson<readonly Notification[]>('/notifications');
}

export function fetchNotificationSettings(): Promise<NotificationSettings> {
  return getJson<NotificationSettings>('/notifications/settings');
}

export function markNotificationsRead(
  body: MarkNotificationsReadRequest,
): Promise<readonly Notification[]> {
  return patchJson<readonly Notification[]>('/notifications/read', body);
}

export function updateNotificationSettings(
  body: UpdateNotificationSettingsRequest,
): Promise<NotificationSettings> {
  return patchJson<NotificationSettings>('/notifications/settings', body);
}
