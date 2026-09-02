import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../../test/render';
import { ExpensesPage } from '../ExpensesPage';

const table = () => screen.getByRole('table', { name: /Expenses by category/ });
const categories = () =>
  within(table())
    .getAllByRole('rowheader')
    .map((cell) => cell.textContent)
    .filter((name) => !name.startsWith('Total'));

describe('ExpensesPage', () => {
  it('titles the page', () => {
    renderWithProviders(<ExpensesPage />);

    expect(screen.getByRole('heading', { level: 1, name: 'Expenses' })).toBeInTheDocument();
  });

  it('shows what each category was paid with, which earnings have no equivalent of', async () => {
    renderWithProviders(<ExpensesPage />);

    await waitFor(() => {
      expect(screen.getByRole('columnheader', { name: 'Paid with' })).toBeInTheDocument();
    });
    expect(within(table()).getAllByText('Visa ·· 4417').length).toBeGreaterThan(0);
  });

  it('colours overspending as bad and underspending as good (BR-9)', async () => {
    renderWithProviders(<ExpensesPage />);

    const over = await screen.findByRole('row', { name: /Groceries/ });
    expect(within(over).getByText('+€18.40').className).toMatch(/bad/);

    const under = screen.getByRole('row', { name: /Transport/ });
    expect(within(under).getByText('−€30.80').className).toMatch(/good/);
  });
});

describe('ExpensesPage - derived rows (BR-14)', () => {
  it('shows the derived debt commitments', async () => {
    renderWithProviders(<ExpensesPage />);

    expect(await screen.findByRole('rowheader', { name: /Card instalments/ })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: /Loan repayments/ })).toBeInTheDocument();
  });

  it('renders a derived row as text, never as an input', async () => {
    renderWithProviders(<ExpensesPage />);
    await screen.findByRole('rowheader', { name: /Card instalments/ });

    expect(
      screen.queryByRole('textbox', { name: 'Planned amount for Card instalments' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('textbox', { name: 'Planned amount for Loan repayments' }),
    ).not.toBeInTheDocument();
  });

  it('still offers an input on a category that is genuinely editable', async () => {
    renderWithProviders(<ExpensesPage />);

    expect(await screen.findByRole('textbox', { name: 'Planned amount for Rent' })).toHaveValue(
      '780.00',
    );
  });
});

describe('ExpensesPage - filtering (BR-15)', () => {
  it('offers each payment method used, plus everything', async () => {
    renderWithProviders(<ExpensesPage />);

    expect(await screen.findByRole('radio', { name: 'All' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Visa ·· 4417' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Wallet' })).toBeInTheDocument();
  });

  it('shows only the rows paid with the chosen method', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExpensesPage />);
    await screen.findByRole('rowheader', { name: /Rent/ });

    await user.click(screen.getByRole('radio', { name: 'Wallet' }));

    expect(categories()).toEqual(['Transport']);
  });

  it('totals the filter rather than the period, and says so', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExpensesPage />);
    await screen.findByRole('rowheader', { name: /Rent/ });

    await user.click(screen.getByRole('radio', { name: 'Wallet' }));

    const totals = screen.getByRole('row', { name: /Total spent/ });
    expect(within(totals).getByText('€105.00')).toBeInTheDocument();
    expect(within(totals).getByText('€74.20')).toBeInTheDocument();
    expect(
      screen.getByText(/Totals below cover Wallet only, not the whole period/),
    ).toBeInTheDocument();
  });

  it('returns to the period totals when the filter is cleared', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExpensesPage />);
    await screen.findByRole('rowheader', { name: /Rent/ });

    await user.click(screen.getByRole('radio', { name: 'Wallet' }));
    await user.click(screen.getByRole('radio', { name: 'All' }));

    const totals = screen.getByRole('row', { name: /Total spent/ });
    expect(within(totals).getByText('€1,634.14')).toBeInTheDocument();
    expect(screen.queryByText(/not the whole period/)).not.toBeInTheDocument();
  });
});

describe('ExpensesPage - grouping and sorting (BR-15)', () => {
  it('groups by the account the money left', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExpensesPage />);
    await screen.findByRole('rowheader', { name: /Rent/ });

    await user.click(screen.getByRole('radio', { name: 'Account' }));

    expect(screen.getByRole('rowheader', { name: 'Wallet' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Revolut Current' })).toBeInTheDocument();
  });

  it('sorts by what was really spent', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExpensesPage />);
    await screen.findByRole('rowheader', { name: /Rent/ });

    await user.click(screen.getByRole('radio', { name: 'Real' }));

    expect(categories()[0]).toMatch(/Rent/);
  });
});

describe('ExpensesPage - editing the plan (BR-14)', () => {
  it('updates the row total as soon as an amount is changed', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExpensesPage />);

    const field = await screen.findByRole('textbox', { name: 'Planned amount for Groceries' });
    await user.clear(field);
    await user.type(field, '80{Enter}');

    // 80.00 a week x 5 = 400.00, without waiting for the server.
    await waitFor(() => {
      const groceries = screen.getByRole('row', { name: /Groceries/ });
      expect(within(groceries).getByText('−€81.60')).toBeInTheDocument();
    });
  });
});
