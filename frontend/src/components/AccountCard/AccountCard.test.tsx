import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { fromDecimal } from '../../lib/money';

import { AccountCard } from './AccountCard';

const savings = {
  name: 'AIB Savings',
  kind: 'SAVINGS',
  balance: fromDecimal('1450'),
} as const;

describe('AccountCard', () => {
  it('names the account and shows its balance', () => {
    render(<AccountCard {...savings} />);

    expect(screen.getByRole('heading', { name: 'AIB Savings' })).toBeInTheDocument();
    expect(screen.getByText('\u20ac1,450.00')).toBeInTheDocument();
  });

  it('labels what kind of account it is', () => {
    render(<AccountCard name="Wallet" kind="CASH" balance={fromDecimal('120')} />);

    expect(screen.getByText('Cash')).toBeInTheDocument();
  });

  it('spells out a bank account rather than abbreviating it', () => {
    render(<AccountCard name="Revolut Current" kind="BANK" balance={fromDecimal('842.30')} />);

    expect(screen.getByText('Bank account')).toBeInTheDocument();
  });

  it('shows the note when there is one', () => {
    render(<AccountCard {...savings} note="ring-fenced for goals" />);

    expect(screen.getByText('ring-fenced for goals')).toBeInTheDocument();
  });

  it('inherits the blueprint frame and its registration marks', () => {
    const { container } = render(<AccountCard {...savings} />);

    expect(container.firstElementChild).toHaveClass('blueprint');
    expect(container.querySelectorAll('.corner')).toHaveLength(4);
  });
});

describe('AccountCard - out of totals (BR-13)', () => {
  it('marks an account that is excluded from the totals', () => {
    render(<AccountCard {...savings} includedInTotals={false} />);

    expect(screen.getByText('out of totals')).toBeInTheDocument();
  });

  it('says nothing when the account counts, which is the normal case', () => {
    render(<AccountCard {...savings} />);

    expect(screen.queryByText('out of totals')).not.toBeInTheDocument();
  });
});

describe('AccountCard - pockets (BR-13)', () => {
  const pockets = [
    { id: 'macbook', name: 'MacBook Air M4', balance: fromDecimal('410') },
    { id: 'emergency', name: 'Emergency fund', balance: fromDecimal('640') },
  ];

  it('lists each pocket and what is in it', () => {
    render(<AccountCard {...savings} pockets={pockets} />);
    const list = screen.getByRole('list', { name: /pockets inside AIB Savings/i });

    expect(within(list).getByText('MacBook Air M4')).toBeInTheDocument();
    expect(within(list).getByText('\u20ac410.00')).toBeInTheDocument();
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
  });

  it('says the pockets are already inside the balance, so they are not read as extra money', () => {
    render(<AccountCard {...savings} pockets={pockets} />);

    expect(screen.getByRole('list', { name: /already part of its balance/i })).toBeInTheDocument();
  });

  it('shows no pocket list when the account has none', () => {
    render(<AccountCard {...savings} />);

    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});

describe('AccountCard - cards', () => {
  it('names the cards that settle from the account', () => {
    render(<AccountCard {...savings} cards={['AIB debit', 'Visa 4417']} />);

    expect(screen.getByText(/AIB debit/)).toBeInTheDocument();
    expect(screen.getByText(/Visa 4417/)).toBeInTheDocument();
  });

  it('says nothing when no card settles from it', () => {
    render(<AccountCard {...savings} />);

    expect(screen.queryByText(/settles/i)).not.toBeInTheDocument();
  });
});
