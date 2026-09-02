import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { fromDecimal } from '../../lib/money';

import { NotificationRow } from './NotificationRow';

const cardBill = {
  label: 'Visa 4417 bill',
  detail: 'closed day 25 - settles from Revolut Current',
  source: 'Card bill',
  amount: fromDecimal('386.40'),
  daysUntilDue: 5,
  isRead: false,
} as const;

describe('NotificationRow', () => {
  it('says what is due, from where, and for how much', () => {
    render(<NotificationRow {...cardBill} onToggleRead={vi.fn()} />);

    expect(screen.getByText('Visa 4417 bill')).toBeInTheDocument();
    expect(screen.getByText(/settles from Revolut Current/)).toBeInTheDocument();
    expect(screen.getByText('\u2212\u20ac386.40')).toBeInTheDocument();
  });

  it('shows money leaving as a negative, because every item here is an outgoing', () => {
    render(<NotificationRow {...cardBill} onToggleRead={vi.fn()} />);

    expect(screen.getByText('\u2212\u20ac386.40')).toBeInTheDocument();
  });

  it('tags where the item came from', () => {
    render(<NotificationRow {...cardBill} onToggleRead={vi.fn()} />);

    expect(screen.getByText('Card bill')).toBeInTheDocument();
  });
});

describe('NotificationRow - how long is left', () => {
  it('counts the days remaining', () => {
    render(<NotificationRow {...cardBill} onToggleRead={vi.fn()} />);

    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('days')).toBeInTheDocument();
  });

  it('uses the singular for one day', () => {
    render(<NotificationRow {...cardBill} daysUntilDue={1} onToggleRead={vi.fn()} />);

    expect(screen.getByText('day')).toBeInTheDocument();
  });

  it('says now when the payment is due today', () => {
    render(<NotificationRow {...cardBill} daysUntilDue={0} onToggleRead={vi.fn()} />);

    expect(screen.getByText('now')).toBeInTheDocument();
  });

  it('says now when the payment is already overdue', () => {
    render(<NotificationRow {...cardBill} daysUntilDue={-3} onToggleRead={vi.fn()} />);

    expect(screen.getByText('now')).toBeInTheDocument();
  });

  it('spells the urgency out for a screen reader rather than relying on colour', () => {
    render(<NotificationRow {...cardBill} daysUntilDue={2} onToggleRead={vi.fn()} />);

    expect(screen.getByText('Due in 2 days')).toBeInTheDocument();
  });

  it('marks an item due within two days as urgent', () => {
    const { container } = render(
      <NotificationRow {...cardBill} daysUntilDue={2} onToggleRead={vi.fn()} />,
    );

    expect(container.firstElementChild?.className).toMatch(/urgent/);
  });

  it('does not mark an item further out as urgent', () => {
    const { container } = render(<NotificationRow {...cardBill} onToggleRead={vi.fn()} />);

    expect(container.firstElementChild?.className).not.toMatch(/urgent/);
  });
});

describe('NotificationRow - read state (BR-12)', () => {
  it('offers to mark an unread item as read', async () => {
    const onToggleRead = vi.fn();
    const user = userEvent.setup();
    render(<NotificationRow {...cardBill} onToggleRead={onToggleRead} />);

    await user.click(screen.getByRole('button', { name: /Mark .* as read/ }));

    expect(onToggleRead).toHaveBeenCalledWith(true);
  });

  it('offers to put a read item back to unread', async () => {
    const onToggleRead = vi.fn();
    const user = userEvent.setup();
    render(<NotificationRow {...cardBill} isRead onToggleRead={onToggleRead} />);

    await user.click(screen.getByRole('button', { name: /Mark .* as unread/ }));

    expect(onToggleRead).toHaveBeenCalledWith(false);
  });

  it('names the item in the button, so a list of them is not a row of identical buttons', () => {
    render(<NotificationRow {...cardBill} onToggleRead={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Mark Visa 4417 bill as read' })).toBeInTheDocument();
  });

  it('recedes once it has been read', () => {
    const { container } = render(<NotificationRow {...cardBill} isRead onToggleRead={vi.fn()} />);

    expect(container.firstElementChild?.className).toMatch(/read/);
  });
});
