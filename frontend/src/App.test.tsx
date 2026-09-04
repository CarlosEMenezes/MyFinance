import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import App from './App';
import { API_BASE } from './test/handlers';
import { renderWithProviders } from './test/render';
import { server } from './test/server';

const render = (route = '/') => renderWithProviders(<App />, { route });

describe('App shell - navigation', () => {
  it('renders both navigations, so the 940px swap needs no JavaScript', () => {
    render();

    // Three landmarks in the tree: the sidebar's Main and Setup, and the tab
    // bar. The stylesheet decides which are offered — no JavaScript reads the
    // viewport, and no component hides itself.
    expect(screen.getAllByRole('navigation', { hidden: true })).toHaveLength(3);
  });

  it('offers only the sidebar on a wide viewport, never both at once', () => {
    render();

    const offered = screen.getAllByRole('navigation');
    expect(offered.map((nav) => nav.getAttribute('aria-label'))).toEqual(['Main', 'Setup']);
  });

  it('separates what the app is for from what configures it', () => {
    render();

    const setup = screen.getByRole('navigation', { name: 'Setup' });
    expect(within(setup).getByRole('link', { name: /Accounts/ })).toBeInTheDocument();
    expect(within(setup).queryByRole('link', { name: /Earnings/ })).not.toBeInTheDocument();
  });

  it('carries only the primary destinations in the tab bar', () => {
    render();

    // Named by class, not by accessible name: a display:none element computes
    // an empty name, so the label cannot be used to reach it at this width.
    const tabBar = screen
      .getAllByRole('navigation', { hidden: true })
      .find((nav) => nav.className.includes('tabbar'));
    expect(within(tabBar as HTMLElement).getAllByRole('link', { hidden: true })).toHaveLength(4);
  });

  it('lets the shell decide whether the sidebar is shown', () => {
    render();

    // Gotcha 23: visibility is the shell's rule, so the component composes the
    // shell's class rather than hiding itself.
    expect(screen.getByRole('complementary').className).toContain('side');
  });

  it('marks the page you are on', async () => {
    render('/expenses');

    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /Expenses/ })[0]).toHaveAttribute(
        'aria-current',
        'page',
      );
    });
  });

  it('does not offer Import, which is not built', () => {
    render();

    expect(screen.queryByRole('link', { name: /Import/ })).not.toBeInTheDocument();
  });
});

describe('App shell - routes', () => {
  it.each([
    ['/', 'Overview'],
    ['/earnings', 'Earnings'],
    ['/expenses', 'Expenses'],
    ['/goals', 'Plan acquisition'],
    ['/accounts', 'Accounts'],
    ['/cards', 'Cards'],
    ['/categories', 'Categories & plan'],
    ['/notifications', 'Notifications'],
    ['/settings', 'Settings'],
  ])('renders %s as the %s page', async (route, title) => {
    render(route);

    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
  });

  it('navigates without a reload when a link is followed', async () => {
    const user = userEvent.setup();
    render();

    await user.click(screen.getAllByRole('link', { name: /Accounts/ })[0] as HTMLElement);

    expect(await screen.findByRole('heading', { level: 1, name: 'Accounts' })).toBeInTheDocument();
  });
});

describe('App shell - the notification badge (BR-12)', () => {
  it('shows the unread count on the alerts link', async () => {
    render();

    // Five unread inside the enabled lead times.
    const alerts = await screen.findByRole('link', { name: /Alerts/ });
    await waitFor(() => {
      expect(alerts).toHaveTextContent('5');
    });
    expect(alerts).toHaveTextContent('unread');
  });

  it('drops the badge as items are read', async () => {
    const user = userEvent.setup();
    render('/notifications');

    await user.click(await screen.findByRole('button', { name: 'Mark all read' }));

    await waitFor(() => {
      expect(screen.getByRole('link', { name: /Alerts/ })).not.toHaveTextContent('unread');
    });
  });
});

describe('App shell - logging an entry', () => {
  it('opens the log dialog from the sidebar', async () => {
    const user = userEvent.setup();
    render();

    await user.click(screen.getByRole('button', { name: '+ Log entry' }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('puts the same action in the phone top bar', () => {
    render();

    // Hidden at this width, present for the narrow one — logging must not be
    // reachable only from a sidebar that a phone never shows.
    expect(screen.getByRole('button', { name: '+ Log', hidden: true })).toBeInTheDocument();
  });

  it('closes the dialog again', async () => {
    const user = userEvent.setup();
    render();

    await user.click(screen.getByRole('button', { name: '+ Log entry' }));
    await user.click(await screen.findByRole('button', { name: 'Cancel' }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('sends the entry in the currency it was logged in, and closes', async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(`${API_BASE}/transactions`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 't-1' }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render();

    await user.click(screen.getByRole('button', { name: '+ Log entry' }));
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Category')).toHaveDisplayValue(/./);
    });

    await user.type(within(dialog).getByLabelText('Amount'), '74.20');
    await user.click(within(dialog).getByRole('button', { name: 'Save entry' }));

    await waitFor(() => {
      // BR-8: no conversion is sent. The rate is the server's to look up and
      // to refuse, so the frontend states only what was actually entered.
      expect(sent).toMatchObject({ type: 'EXPENSE', amount: 7420, currency: 'EUR' });
    });
    expect(sent).not.toHaveProperty('amountInDefaultCurrency');
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('offers the real categories and payment methods', async () => {
    const user = userEvent.setup();
    render();

    await user.click(screen.getByRole('button', { name: '+ Log entry' }));
    const dialog = await screen.findByRole('dialog');

    await waitFor(() => {
      expect(within(dialog).getByLabelText(/Category/)).toHaveDisplayValue(/./);
    });
    expect(within(dialog).getByLabelText(/Paid with|Payment method/)).toBeInTheDocument();
  });
});
