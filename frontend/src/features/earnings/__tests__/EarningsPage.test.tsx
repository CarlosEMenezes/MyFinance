import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../../test/render';
import { EarningsPage } from '../EarningsPage';

const table = () => screen.getByRole('table', { name: /Earnings by category/ });
// The totals row carries a rowheader too, so category rows are the ones that
// are not it.
const rowNames = () =>
  within(table())
    .getAllByRole('rowheader')
    .map((cell) => cell.textContent)
    .filter((name) => !name.startsWith('Total'));

describe('EarningsPage', () => {
  it('titles the page', () => {
    renderWithProviders(<EarningsPage />);

    expect(screen.getByRole('heading', { level: 1, name: 'Earnings' })).toBeInTheDocument();
  });

  it('lists every earning category with what was really earned', async () => {
    renderWithProviders(<EarningsPage />);

    await waitFor(() => {
      expect(screen.getByRole('rowheader', { name: /Freelance design/ })).toBeInTheDocument();
    });
    expect(screen.getByText('€1,010.00')).toBeInTheDocument();
    expect(screen.getByText('€672.00')).toBeInTheDocument();
  });

  it('colours earning under plan as bad and over plan as good (BR-9)', async () => {
    renderWithProviders(<EarningsPage />);

    const behind = await screen.findByRole('row', { name: /Freelance design/ });
    expect(within(behind).getByText('−€190.00').className).toMatch(/bad/);

    const ahead = screen.getByRole('row', { name: /Part-time café/ });
    expect(within(ahead).getByText('+€32.00').className).toMatch(/good/);
  });

  it('tags an amount that was logged in another currency (BR-8)', async () => {
    renderWithProviders(<EarningsPage />);

    expect(await screen.findByText('USD 456.00')).toBeInTheDocument();
  });

  it('says how often a plan lands, so a planned total can be explained (BR-10)', async () => {
    renderWithProviders(<EarningsPage />);

    expect(await screen.findByText(/planned each weekly × 5/)).toBeInTheDocument();
  });

  it('shows the totals the server sent rather than summing the rows (BR-15)', async () => {
    renderWithProviders(<EarningsPage />);

    const totals = await screen.findByRole('row', { name: /Total earned/ });
    expect(within(totals).getByText('€2,065.00')).toBeInTheDocument();
    expect(within(totals).getByText('€1,866.50')).toBeInTheDocument();
  });
});

describe('EarningsPage - view state (BR-15)', () => {
  it('sorts by category name to begin with', async () => {
    renderWithProviders(<EarningsPage />);

    await waitFor(() => {
      expect(rowNames()[0]).toMatch(/Freelance design/);
    });
    expect(rowNames()).toHaveLength(4);
  });

  it('sorts by what was really earned when asked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<EarningsPage />);
    await screen.findByRole('rowheader', { name: /Freelance design/ });

    await user.click(screen.getByRole('radio', { name: 'Real' }));

    expect(rowNames()[0]).toMatch(/Freelance design/);
    expect(rowNames()[1]).toMatch(/Part-time café/);
  });

  it('sorts by the size of the variance, whichever way it points', async () => {
    const user = userEvent.setup();
    renderWithProviders(<EarningsPage />);
    await screen.findByRole('rowheader', { name: /Freelance design/ });

    await user.click(screen.getByRole('radio', { name: 'Variance' }));

    // −190.00 is a bigger miss than the +64.50 that is entirely unplanned.
    expect(rowNames()[0]).toMatch(/Freelance design/);
  });

  it('groups by category group when asked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<EarningsPage />);
    await screen.findByRole('rowheader', { name: /Freelance design/ });

    await user.click(screen.getByRole('radio', { name: 'Group' }));

    expect(screen.getByRole('rowheader', { name: 'Employment' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Self-employed' })).toBeInTheDocument();
  });

  it('groups by how often the plan recurs when asked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<EarningsPage />);
    await screen.findByRole('rowheader', { name: /Freelance design/ });

    await user.click(screen.getByRole('radio', { name: 'Frequency' }));

    expect(screen.getByRole('rowheader', { name: 'Monthly' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Weekly' })).toBeInTheDocument();
  });

  it('offers no payment-method axis, because money arriving has none', async () => {
    renderWithProviders(<EarningsPage />);
    await screen.findByRole('rowheader', { name: /Freelance design/ });

    expect(screen.queryByRole('columnheader', { name: 'Paid with' })).not.toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: 'Account' })).not.toBeInTheDocument();
  });
});
