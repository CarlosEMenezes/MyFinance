import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import type { NavItem } from '../../types/navigation';

import { SidebarNav } from './SidebarNav';

const icon = <svg aria-hidden="true" />;

const primary: NavItem[] = [
  { to: '/', label: 'Overview', icon },
  { to: '/earnings', label: 'Earnings', icon },
  { to: '/expenses', label: 'Expenses', icon },
  { to: '/goals', label: 'Plan', icon },
];

const setup: NavItem[] = [
  { to: '/accounts', label: 'Accounts', icon },
  { to: '/cards', label: 'Cards', icon },
  { to: '/notifications', label: 'Alerts', icon, badge: 3 },
  { to: '/settings', label: 'Settings', icon },
];

const show = (path = '/', overrides = {}) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <SidebarNav
        primary={primary}
        setup={setup}
        onLogEntry={vi.fn()}
        defaultCurrency="EUR"
        fxUpdatedAt="31-08-2026 08:12"
        {...overrides}
      />
    </MemoryRouter>,
  );

describe('SidebarNav', () => {
  it('carries the product name', () => {
    show();

    expect(screen.getByText('BUDGET TRACKER')).toBeInTheDocument();
  });

  it('offers every primary destination as a link', () => {
    show();
    const main = screen.getByRole('navigation', { name: 'Main' });

    expect(within(main).getByRole('link', { name: /Overview/ })).toHaveAttribute('href', '/');
    expect(within(main).getByRole('link', { name: /Expenses/ })).toHaveAttribute(
      'href',
      '/expenses',
    );
  });

  it('separates the setup destinations from the primary ones', () => {
    show();
    const setupNav = screen.getByRole('navigation', { name: 'Setup' });

    expect(within(setupNav).getByRole('link', { name: /Cards/ })).toBeInTheDocument();
    expect(within(setupNav).queryByRole('link', { name: /Overview/ })).not.toBeInTheDocument();
  });

  it('marks the page you are on, rather than relying on colour alone', () => {
    show('/expenses');

    expect(screen.getByRole('link', { name: /Expenses/ })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /Overview/ })).not.toHaveAttribute('aria-current');
  });

  it('shows how many notifications are unread', () => {
    show();

    expect(screen.getByRole('link', { name: /Alerts/ })).toHaveTextContent('3');
  });

  it('says what the unread count means, so a bare number is not read out alone', () => {
    show();

    expect(screen.getByRole('link', { name: /3 unread/ })).toBeInTheDocument();
  });

  it('shows no badge when nothing is waiting', () => {
    show('/', { setup: setup.map((item) => ({ ...item, badge: 0 })) });

    expect(screen.queryByText('3')).not.toBeInTheDocument();
  });

  it('offers the log-entry action', async () => {
    const onLogEntry = vi.fn();
    const user = userEvent.setup();
    show('/', { onLogEntry });

    await user.click(screen.getByRole('button', { name: /Log entry/ }));

    expect(onLogEntry).toHaveBeenCalledTimes(1);
  });

  it('states the default currency and how fresh the rates are (BR-8)', () => {
    show();

    expect(screen.getByText(/Default currency EUR/)).toBeInTheDocument();
    expect(screen.getByText(/31-08-2026 08:12/)).toBeInTheDocument();
  });
});
