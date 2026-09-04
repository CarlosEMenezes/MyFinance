import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { NotificationsPage } from '../NotificationsPage';

const render = () => renderWithProviders(<NotificationsPage />);

/** The queue as rendered, in order. */
const queueItems = async (): Promise<HTMLElement[]> => {
  const list = await screen.findByRole('list');
  return within(list).getAllByRole('listitem');
};

describe('NotificationsPage - the derived queue (BR-12)', () => {
  it('titles the page', () => {
    render();

    expect(screen.getByRole('heading', { level: 1, name: 'Notifications' })).toBeInTheDocument();
  });

  it('shows only what falls inside the widest enabled lead time', async () => {
    render();

    // Rent is one day out, inside every lead time.
    expect(await screen.findByText('Rent')).toBeInTheDocument();
    // The gym is twelve days out, past the widest lead time of ten.
    expect(screen.queryByText('Gym')).not.toBeInTheDocument();
  });

  it('keeps the order the server sorted it into, soonest first', async () => {
    render();
    const items = await queueItems();

    expect(items[0]).toHaveTextContent('Rent');
    expect(items[1]).toHaveTextContent('Spotify + iCloud');
    expect(items.at(-1)).toHaveTextContent('Credit union loan repayment');
  });

  it('states when each item falls due, not only how soon', async () => {
    render();
    const items = await queueItems();

    expect(items[0]).toHaveTextContent('01-09-2026');
  });

  it('names where each item came from', async () => {
    render();
    const items = await queueItems();

    expect(items[0]).toHaveTextContent('Direct debit');
    expect(items[1]).toHaveTextContent('Subscription');
  });

  it('counts the unread items in the heading', async () => {
    render();

    // Six visible, of which the credit-union repayment is already read.
    expect(await screen.findByText('5 unread · 6 due soon')).toBeInTheDocument();
  });

  it('says the queue could not be loaded rather than showing an empty one', async () => {
    server.use(
      http.get(`${API_BASE}/notifications`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Queue unavailable', status: 503, detail: '' },
          { status: 503 },
        ),
      ),
    );
    render();

    expect(await screen.findByText('Queue unavailable')).toBeInTheDocument();
  });
});

describe('NotificationsPage - read state', () => {
  it('marks one item read and keeps it read after a refetch', async () => {
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Mark Rent as read' }));

    expect(await screen.findByRole('button', { name: 'Mark Rent as unread' })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText('4 unread · 6 due soon')).toBeInTheDocument();
    });
  });

  it('marks an item unread again', async () => {
    const user = userEvent.setup();
    render();

    await user.click(
      await screen.findByRole('button', { name: 'Mark Credit union loan repayment as unread' }),
    );

    expect(
      await screen.findByRole('button', { name: 'Mark Credit union loan repayment as read' }),
    ).toBeInTheDocument();
  });

  it('rolls the row back when the write fails', async () => {
    server.use(
      http.patch(`${API_BASE}/notifications/read`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Could not save', status: 500, detail: '' },
          { status: 500 },
        ),
      ),
    );
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Mark Rent as read' }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Mark Rent as read' })).toBeInTheDocument();
    });
  });

  it('marks everything visible read at once', async () => {
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Mark all read' }));

    await waitFor(() => {
      expect(screen.getByText('6 due soon')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: 'Mark all read' })).toBeDisabled();
  });

  it('marks read only what is on screen, never what the lead times hide', async () => {
    let marked: readonly string[] = [];
    server.use(
      http.patch(`${API_BASE}/notifications/read`, async ({ request }) => {
        marked = ((await request.json()) as { keys: string[] }).keys;
        return HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Mark all read' }));

    // The gym is twelve days out and not on screen; marking all read must not
    // silently clear a warning the user never saw.
    await waitFor(() => {
      expect(marked).not.toHaveLength(0);
    });
    expect(marked).not.toContain('gym');
    // Nor the item that was already read.
    expect(marked).not.toContain('loan-credit-union');
  });
});

describe('NotificationsPage - lead times (BR-12)', () => {
  it('says how many items each lead time catches', async () => {
    render();

    const ten = await screen.findByRole('checkbox', { name: /10 days before due/ });
    const two = screen.getByRole('checkbox', { name: /2 days before due/ });

    expect(ten).toBeChecked();
    expect(ten.closest('label')).toHaveTextContent('6 items');
    expect(two.closest('label')).toHaveTextContent('1 item');
  });

  it('narrows the queue when the widest lead time is turned off', async () => {
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('checkbox', { name: /10 days before due/ }));

    // Five days is now the widest, so the loan repayment at six drops out.
    await waitFor(() => {
      expect(screen.queryByText('Credit union loan repayment')).not.toBeInTheDocument();
    });
    expect(screen.getByText('Rent')).toBeInTheDocument();
  });

  it('shows nothing due when every lead time is off and nothing is overdue', async () => {
    const user = userEvent.setup();
    render();

    for (const days of [10, 5, 2]) {
      await user.click(
        await screen.findByRole('checkbox', {
          name: new RegExp(`${String(days)} days before due`),
        }),
      );
    }

    expect(await screen.findByText('Nothing due inside your lead times')).toBeInTheDocument();
  });

  it('keeps a narrowed lead time after a refetch', async () => {
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('checkbox', { name: /10 days before due/ }));

    await waitFor(() => {
      expect(screen.getByRole('checkbox', { name: /10 days before due/ })).not.toBeChecked();
    });
  });

  it('rolls a lead time back when the write fails', async () => {
    server.use(
      http.patch(`${API_BASE}/notifications/settings`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Could not save', status: 500, detail: '' },
          { status: 500 },
        ),
      ),
    );
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('checkbox', { name: /10 days before due/ }));

    await waitFor(() => {
      expect(screen.getByRole('checkbox', { name: /10 days before due/ })).toBeChecked();
    });
  });
});

describe('NotificationsPage - channels', () => {
  it('shows which channels are on', async () => {
    render();

    expect(await screen.findByRole('checkbox', { name: /Push notification/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /^Email/ })).not.toBeChecked();
  });

  it('turns a channel on', async () => {
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('checkbox', { name: /^Email/ }));

    await waitFor(() => {
      expect(screen.getByRole('checkbox', { name: /^Email/ })).toBeChecked();
    });
  });
});
