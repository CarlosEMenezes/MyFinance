import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import type { NavItem } from '../../types/navigation';

import { BottomTabBar } from './BottomTabBar';

const icon = <svg aria-hidden="true" />;

const items: NavItem[] = [
  { to: '/', label: 'Overview', icon },
  { to: '/earnings', label: 'Earnings', icon },
  { to: '/expenses', label: 'Expenses', icon },
  { to: '/goals', label: 'Plan', icon },
];

const show = (path = '/', overrides: Partial<{ items: NavItem[] }> = {}) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <BottomTabBar items={items} {...overrides} />
    </MemoryRouter>,
  );

describe('BottomTabBar', () => {
  it('is a named landmark, distinct from the sidebar', () => {
    show();

    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument();
  });

  it('offers each destination as a link', () => {
    show();

    expect(screen.getByRole('link', { name: 'Earnings' })).toHaveAttribute('href', '/earnings');
    expect(screen.getAllByRole('link')).toHaveLength(4);
  });

  it('marks the page you are on', () => {
    show('/goals');

    expect(screen.getByRole('link', { name: 'Plan' })).toHaveAttribute('aria-current', 'page');
  });

  it('does not mark Overview as current on another page, despite matching its prefix', () => {
    show('/expenses');

    expect(screen.getByRole('link', { name: 'Overview' })).not.toHaveAttribute('aria-current');
  });

  it('shows an unread count when there is one', () => {
    show('/', { items: [...items, { to: '/notifications', label: 'Alerts', icon, badge: 2 }] });

    expect(screen.getByRole('link', { name: /2 unread/ })).toBeInTheDocument();
  });
});
