import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { fromIso } from '../../lib/dates';
import { fromDecimal } from '../../lib/money';

import { CardSummary } from './CardSummary';

const visa = {
  kind: 'CREDIT',
  name: 'Visa 4417',
  settlesFrom: 'Revolut Current',
  creditLimit: fromDecimal('2000'),
  currentBalance: fromDecimal('386.40'),
  closingDay: 25,
  dueDay: 5,
  cycle: {
    nextBillDate: fromIso('2026-09-05'),
    billDateOnClosingDay: fromIso('2026-09-05'),
    billDateAfterClosingDay: fromIso('2026-10-05'),
  },
} as const;

const debit = {
  kind: 'DEBIT',
  name: 'Revolut debit',
  settlesFrom: 'Revolut Current',
} as const;

describe('CardSummary - both kinds', () => {
  it('names the card and where it settles from', () => {
    render(<CardSummary {...visa} />);

    expect(screen.getByRole('heading', { name: 'Visa 4417' })).toBeInTheDocument();
    expect(screen.getByText(/Revolut Current/)).toBeInTheDocument();
  });

  it('labels which kind of card it is', () => {
    render(<CardSummary {...debit} />);

    expect(screen.getByText('Debit')).toBeInTheDocument();
  });

  it('inherits the blueprint frame and its registration marks', () => {
    const { container } = render(<CardSummary {...visa} />);

    expect(container.firstElementChild).toHaveClass('blueprint');
    expect(container.querySelectorAll('.corner')).toHaveLength(4);
  });
});

describe('CardSummary - a credit card', () => {
  it('shows what is used against the limit, and what is left', () => {
    // The figures are MoneyText spans inside the sentence, so the assertion is
    // on the composed text rather than on a single node.
    const { container } = render(<CardSummary {...visa} />);

    expect(container.textContent).toContain('€386.40 used of €2,000.00');
    expect(container.textContent).toContain('€1,613.60 available');
  });

  it('draws the usage as a bar', () => {
    render(<CardSummary {...visa} />);

    expect(screen.getByRole('progressbar', { name: /Visa 4417/ })).toHaveAttribute(
      'aria-valuenow',
      '19',
    );
  });

  it('keeps the ordinary tone while there is room on the card', () => {
    const { container } = render(<CardSummary {...visa} />);

    expect(container.querySelector('[data-testid="fill"]')?.className).toMatch(/accent/);
  });

  it('turns to the over-plan tone when the card is running out of room', () => {
    const { container } = render(<CardSummary {...visa} currentBalance={fromDecimal('1700')} />);

    expect(container.querySelector('[data-testid="fill"]')?.className).toMatch(/negative/);
  });

  it('shows an empty bar rather than dividing by zero on a card with no limit set', () => {
    render(<CardSummary {...visa} creditLimit={fromDecimal('0')} />);

    expect(screen.getByRole('progressbar', { name: /Visa 4417/ })).toHaveAttribute(
      'aria-valuenow',
      '0',
    );
  });

  it('states the closing and due days', () => {
    render(<CardSummary {...visa} />);

    expect(screen.getByText('day 25')).toBeInTheDocument();
    expect(screen.getByText('day 5')).toBeInTheDocument();
  });

  it('says when the next bill actually falls', () => {
    render(<CardSummary {...visa} />);

    expect(screen.getByText('05-09-2026')).toBeInTheDocument();
  });

  it('explains the cycle with the real dates rather than in the abstract (BR-4)', () => {
    render(<CardSummary {...visa} />);

    expect(screen.getByText(/up to day 25 is billed on 05-09-2026/)).toBeInTheDocument();
    expect(screen.getByText(/waits for the next bill \(05-10-2026\)/)).toBeInTheDocument();
  });
});

describe('CardSummary - a debit card (BR-5)', () => {
  it('says the money leaves the account the same day', () => {
    render(<CardSummary {...debit} />);

    expect(screen.getByText(/leaves Revolut Current the same day/)).toBeInTheDocument();
  });

  it('shows no statement cycle, because a debit card has none', () => {
    render(<CardSummary {...debit} />);

    expect(screen.queryByText(/Closes/)).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });
});
