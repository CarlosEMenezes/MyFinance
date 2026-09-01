import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { fromDecimal } from '../../lib/money';

import { PlanVsRealTable } from './PlanVsRealTable';
import type { PlanVsRealRow, PlanVsRealTableProps } from './PlanVsRealTable.types';

const groceries: PlanVsRealRow = {
  id: 'groceries',
  category: 'Groceries',
  type: 'EXPENSE',
  planned: fromDecimal('300'),
  real: fromDecimal('318.40'),
  planNote: 'each week x 5',
  perOccurrence: fromDecimal('60'),
  paidWith: 'Visa 4417',
};

const rent: PlanVsRealRow = {
  id: 'rent',
  category: 'Rent',
  type: 'EXPENSE',
  planned: fromDecimal('780'),
  real: fromDecimal('780'),
  planNote: 'once this month',
  perOccurrence: fromDecimal('780'),
  paidWith: 'Revolut Current',
  dueNote: 'due 01-09',
};

const cardInstalments: PlanVsRealRow = {
  id: 'card-instalments',
  category: 'Card instalments',
  type: 'EXPENSE',
  planned: fromDecimal('185.74'),
  real: fromDecimal('185.74'),
  planNote: 'this period',
  locked: true,
  paidWith: 'Visa 4417',
};

const renderTable = (overrides: Partial<PlanVsRealTableProps> = {}) =>
  render(
    <PlanVsRealTable
      caption="Expenses by category"
      rows={[groceries, rent]}
      showPaymentMethod
      totalLabel="Total spent"
      totalType="EXPENSE"
      totalPlanned={fromDecimal('1080')}
      totalReal={fromDecimal('1098.40')}
      {...overrides}
    />,
  );

describe('PlanVsRealTable - structure', () => {
  it('is a table with an accessible caption', () => {
    renderTable();

    expect(screen.getByRole('table', { name: 'Expenses by category' })).toBeInTheDocument();
  });

  it('heads the columns', () => {
    renderTable();

    expect(screen.getByRole('columnheader', { name: 'Category' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Paid with' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Planned / each' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Real' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Variance' })).toBeInTheDocument();
  });

  it('drops the payment-method column for earnings', () => {
    renderTable({ showPaymentMethod: false });

    expect(screen.queryByRole('columnheader', { name: 'Paid with' })).not.toBeInTheDocument();
  });

  it('names each category as its row header', () => {
    renderTable();

    expect(screen.getByRole('rowheader', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Rent' })).toBeInTheDocument();
  });

  it('gives every category a ghost planned line above its real line', () => {
    renderTable();

    expect(screen.getByText(/planned each week x 5/)).toBeInTheDocument();
    expect(screen.getByText(/planned once this month/)).toBeInTheDocument();
  });
});

describe('PlanVsRealTable - the figures', () => {
  it('shows what was really spent', () => {
    renderTable();

    expect(screen.getByText('\u20ac318.40')).toBeInTheDocument();
  });

  it('colours overspending as bad and matching the plan as neutral', () => {
    renderTable();

    const overspent = screen.getByRole('row', { name: /Groceries/ });
    const onPlan = screen.getByRole('row', { name: /Rent/ });
    expect(within(overspent).getByText('+€18.40').className).toMatch(/bad/);
    expect(within(onPlan).getByText('€0.00').className).toMatch(/neutral/);
  });

  it('shows the payment method used', () => {
    renderTable();

    expect(screen.getAllByText('Visa 4417').length).toBeGreaterThan(0);
  });

  it('tags a converted amount with the currency it was logged in', () => {
    renderTable({ rows: [{ ...groceries, foreignAmount: 'USD 456.00' }] });

    expect(screen.getByText('USD 456.00')).toBeInTheDocument();
  });
});

describe('PlanVsRealTable - editing the plan (BR-14)', () => {
  it('offers an input for the per-occurrence amount', () => {
    renderTable({ onPlanChange: vi.fn() });

    expect(screen.getByRole('textbox', { name: 'Planned amount for Groceries' })).toHaveValue(
      '60.00',
    );
  });

  it('reports which row was edited and the parsed amount', async () => {
    const onPlanChange = vi.fn();
    const user = userEvent.setup();
    renderTable({ onPlanChange });

    const field = screen.getByRole('textbox', { name: 'Planned amount for Groceries' });
    await user.clear(field);
    await user.type(field, '75{Enter}');

    expect(onPlanChange).toHaveBeenCalledWith('groceries', fromDecimal('75'));
  });

  it('renders a derived row as text, never as an input', () => {
    renderTable({ rows: [cardInstalments], onPlanChange: vi.fn() });

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getAllByText('\u20ac185.74').length).toBeGreaterThan(0);
  });

  it('renders every row as text when no edit handler is supplied', () => {
    renderTable();

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
});

describe('PlanVsRealTable - grouping (BR-15)', () => {
  it('renders a heading above the first row of a group', () => {
    renderTable({
      rows: [
        { ...rent, groupHeading: 'Fixed' },
        { ...groceries, groupHeading: 'Variable' },
      ],
    });

    expect(screen.getByRole('rowheader', { name: 'Fixed' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Variable' })).toBeInTheDocument();
  });

  it('renders no headings when the rows are ungrouped', () => {
    renderTable();

    expect(screen.queryByRole('rowheader', { name: 'Fixed' })).not.toBeInTheDocument();
  });
});

describe('PlanVsRealTable - totals', () => {
  it('shows the totals it is given rather than summing the visible rows', () => {
    renderTable();
    const footer = screen.getByRole('row', { name: /Total spent/ });

    expect(within(footer).getByText('\u20ac1,080.00')).toBeInTheDocument();
    expect(within(footer).getByText('\u20ac1,098.40')).toBeInTheDocument();
  });

  it('colours the total variance by the same convention as a row', () => {
    renderTable();
    const footer = screen.getByRole('row', { name: /Total spent/ });

    expect(within(footer).getByText('+\u20ac18.40').className).toMatch(/bad/);
  });

  it('renders headers and totals even with no rows', () => {
    renderTable({ rows: [] });

    expect(screen.getByRole('columnheader', { name: 'Category' })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Total spent/ })).toBeInTheDocument();
  });
});
