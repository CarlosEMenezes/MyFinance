import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { fromIso } from '../../lib/dates';
import { fromDecimal } from '../../lib/money';

import { LogEntryForm } from './LogEntryForm';
import type { LogEntryFormProps } from './LogEntryForm.types';

const categories = [
  { id: 'groceries', name: 'Groceries', type: 'EXPENSE' },
  { id: 'rent', name: 'Rent', type: 'EXPENSE' },
  { id: 'freelance', name: 'Freelance design', type: 'EARNING' },
  { id: 'macbook', name: 'MacBook Air M4', type: 'SAVING' },
] as const;

const paymentMethods = [
  { id: 'wallet', name: 'Wallet', kind: 'ACCOUNT' },
  { id: 'revolut', name: 'Revolut Current', kind: 'ACCOUNT' },
  { id: 'visa', name: 'Visa 4417', kind: 'CREDIT_CARD', closingDay: 25, dueDay: 5 },
  { id: 'revolut-debit', name: 'Revolut debit', kind: 'DEBIT_CARD' },
] as const;

const fxReady = {
  status: 'READY',
  rates: { USD: 0.921, GBP: 1.172, BRL: 0.171 },
  fetchedAt: '31-08-2026 08:12',
} as const;

const show = (overrides: Partial<LogEntryFormProps> = {}) =>
  render(
    <LogEntryForm
      open
      onClose={vi.fn()}
      onSubmit={vi.fn()}
      categories={categories}
      paymentMethods={paymentMethods}
      defaultCurrency="EUR"
      fx={fxReady}
      today={fromIso('2026-08-31')}
      {...overrides}
    />,
  );

describe('LogEntryForm - shape', () => {
  it('opens as a dialog', () => {
    show();

    expect(screen.getByRole('dialog', { name: 'Log entry' })).toBeInTheDocument();
  });

  it('offers the three kinds of entry', () => {
    show();

    expect(screen.getByRole('radio', { name: 'Expense' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Earning' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Saving' })).toBeInTheDocument();
  });

  it('offers only the categories that belong to the chosen kind', async () => {
    const user = userEvent.setup();
    show();

    expect(screen.getByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Freelance design' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'Earning' }));

    expect(screen.getByRole('option', { name: 'Freelance design' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Groceries' })).not.toBeInTheDocument();
  });

  it('names the payment field for what the money is doing', async () => {
    const user = userEvent.setup();
    show();

    expect(screen.getByLabelText('Paid with')).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'Earning' }));
    expect(screen.getByLabelText('Paid into')).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'Saving' }));
    expect(screen.getByLabelText('Save into')).toBeInTheDocument();
  });
});

describe('LogEntryForm - foreign currency (BR-8)', () => {
  it('says nothing about conversion while the entry is in the default currency', async () => {
    const user = userEvent.setup();
    show();

    await user.type(screen.getByLabelText('Amount'), '50');

    expect(screen.queryByText(/at today's rate/)).not.toBeInTheDocument();
  });

  it('previews the converted amount and the rate it used', async () => {
    const user = userEvent.setup();
    show();

    await user.type(screen.getByLabelText('Amount'), '100');
    await user.selectOptions(screen.getByLabelText('Currency'), 'USD');

    expect(screen.getByText(/\u20ac92\.10/)).toBeInTheDocument();
    expect(screen.getByText(/1 USD = 0\.921 EUR/)).toBeInTheDocument();
  });

  it('shows when the rate was last pulled, so its freshness is visible', async () => {
    const user = userEvent.setup();
    show();

    await user.type(screen.getByLabelText('Amount'), '100');
    await user.selectOptions(screen.getByLabelText('Currency'), 'USD');

    expect(screen.getByText(/31-08-2026 08:12/)).toBeInTheDocument();
  });

  it('blocks the save with a clear error when no rate can be had, rather than guessing', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ fx: { status: 'UNAVAILABLE' }, onSubmit });

    await user.type(screen.getByLabelText('Amount'), '100');
    await user.selectOptions(screen.getByLabelText('Currency'), 'USD');
    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(/exchange rate/i);
  });

  it('still saves in the default currency when the rate provider is down', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ fx: { status: 'UNAVAILABLE' }, onSubmit });

    await user.type(screen.getByLabelText('Amount'), '50');
    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
  });
});

describe('LogEntryForm - card timing (BR-4)', () => {
  it('says when a card purchase will actually be billed, not when it was spent', async () => {
    const user = userEvent.setup();
    show();

    await user.selectOptions(screen.getByLabelText('Paid with'), 'visa');

    // Spent 31-08 on a card closing 25 and due 5: past closing, so it waits
    // for the next statement and lands on 05-10-2026.
    expect(screen.getByText(/becomes a planned expense on 05-10-2026/)).toBeInTheDocument();
    expect(screen.getByText(/not on 31-08-2026/)).toBeInTheDocument();
  });

  it('says nothing about billing when the money leaves an account directly', async () => {
    const user = userEvent.setup();
    show();

    await user.selectOptions(screen.getByLabelText('Paid with'), 'revolut');

    expect(screen.queryByText(/planned expense on/)).not.toBeInTheDocument();
  });

  it('says nothing about billing for a debit card, which has no cycle (BR-5)', async () => {
    const user = userEvent.setup();
    show();

    await user.selectOptions(screen.getByLabelText('Paid with'), 'revolut-debit');

    expect(screen.queryByText(/planned expense on/)).not.toBeInTheDocument();
  });
});

describe('LogEntryForm - financing', () => {
  it('offers to spread the cost when paying by credit card', async () => {
    const user = userEvent.setup();
    show();

    await user.selectOptions(screen.getByLabelText('Paid with'), 'visa');

    expect(
      screen.getByRole('checkbox', { name: /Pay in instalments on this card/ }),
    ).toBeInTheDocument();
  });

  it('does not offer to spread the cost when paying from an account', async () => {
    const user = userEvent.setup();
    show();

    await user.selectOptions(screen.getByLabelText('Paid with'), 'wallet');

    expect(screen.queryByRole('checkbox', { name: /instalments/ })).not.toBeInTheDocument();
  });

  it('offers to record an earning as a loan, because borrowing is not income (BR-2)', async () => {
    const user = userEvent.setup();
    show();

    await user.click(screen.getByRole('radio', { name: 'Earning' }));

    expect(screen.getByRole('checkbox', { name: /This is a loan/ })).toBeInTheDocument();
  });

  it('reveals the calculator once financing is turned on', async () => {
    const user = userEvent.setup();
    show();

    await user.type(screen.getByLabelText('Amount'), '399');
    await user.selectOptions(screen.getByLabelText('Paid with'), 'visa');
    await user.click(screen.getByRole('checkbox', { name: /Pay in instalments/ }));

    expect(screen.getByLabelText('Number of instalments')).toBeInTheDocument();
  });
});

describe('LogEntryForm - saving the entry', () => {
  it('emits what was logged', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ onSubmit });

    await user.type(screen.getByLabelText('Amount'), '74.20');
    await user.selectOptions(screen.getByLabelText('Category'), 'groceries');
    await user.selectOptions(screen.getByLabelText('Paid with'), 'wallet');
    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).toHaveBeenCalledWith({
      type: 'EXPENSE',
      amount: fromDecimal('74.20'),
      currency: 'EUR',
      categoryId: 'groceries',
      paymentMethodId: 'wallet',
      date: fromIso('2026-08-31'),
      financing: null,
    });
  });

  it('refuses an entry with no amount', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ onSubmit });

    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(/amount/i);
  });

  it('refuses an amount that is not a number', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ onSubmit });

    await user.type(screen.getByLabelText('Amount'), 'lots');
    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('closes without saving when cancelled', async () => {
    const onClose = vi.fn();
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ onClose, onSubmit });

    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onSubmit).not.toHaveBeenCalled();
  });
});

describe('LogEntryForm - saving a financed entry', () => {
  it('carries the loan terms through with the entry (BR-2, BR-7)', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ onSubmit });

    await user.click(screen.getByRole('radio', { name: 'Earning' }));
    await user.type(screen.getByLabelText('Amount'), '2500');
    await user.click(screen.getByRole('checkbox', { name: /This is a loan/ }));

    const months = screen.getByLabelText('Duration in months');
    await user.clear(months);
    await user.type(months, '24');
    await user.type(screen.getByLabelText('Amount per instalment'), '118.40');
    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'EARNING',
        financing: {
          instalmentCount: 24,
          instalmentAmount: fromDecimal('118.40'),
          frequency: 'MONTHLY',
        },
      }),
    );
  });

  it('saves a plain entry when financing is switched on but never filled in', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ onSubmit });

    await user.click(screen.getByRole('radio', { name: 'Earning' }));
    await user.type(screen.getByLabelText('Amount'), '450');
    await user.click(screen.getByRole('checkbox', { name: /This is a loan/ }));
    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ financing: null }));
  });

  it('ignores an instalment count that is not a whole number', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    show({ onSubmit });

    await user.click(screen.getByRole('radio', { name: 'Earning' }));
    await user.type(screen.getByLabelText('Amount'), '600');
    await user.click(screen.getByRole('checkbox', { name: /This is a loan/ }));

    const months = screen.getByLabelText('Duration in months');
    await user.clear(months);
    await user.type(months, '2.5');
    await user.type(screen.getByLabelText('Amount per instalment'), '100');
    await user.click(screen.getByRole('button', { name: 'Save entry' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ financing: null }));
  });
});
