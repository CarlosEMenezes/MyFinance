import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { CategoriesPage } from '../CategoriesPage';

const expensesTable = () => screen.getByRole('table', { name: /Expense categories/ });

describe('CategoriesPage', () => {
  it('titles the page', () => {
    renderWithProviders(<CategoriesPage />);

    expect(
      screen.getByRole('heading', { level: 1, name: 'Categories & plan' }),
    ).toBeInTheDocument();
  });

  it('separates expense categories from earning ones', async () => {
    renderWithProviders(<CategoriesPage />);

    await waitFor(() => {
      expect(within(expensesTable()).getByRole('rowheader', { name: 'Rent' })).toBeInTheDocument();
    });
    const earnings = screen.getByRole('table', { name: /Earning categories/ });
    expect(within(earnings).getByRole('rowheader', { name: 'Part-time café' })).toBeInTheDocument();
    expect(within(earnings).queryByRole('rowheader', { name: 'Rent' })).not.toBeInTheDocument();
  });

  it('leaves archived categories out', async () => {
    renderWithProviders(<CategoriesPage />);

    await waitFor(() => {
      expect(screen.getByRole('rowheader', { name: 'Rent' })).toBeInTheDocument();
    });
    expect(screen.queryByRole('rowheader', { name: 'Old subscription' })).not.toBeInTheDocument();
  });
});

describe('CategoriesPage - what the plan comes to (BR-10)', () => {
  it('counts how many times each plan lands in the window', async () => {
    renderWithProviders(<CategoriesPage />);

    // Groceries is weekly from 05-01-2026; August 2026 holds five of them.
    const groceries = await screen.findByRole('row', { name: /Groceries/ });
    expect(within(groceries).getByText('×5')).toBeInTheDocument();

    const rent = screen.getByRole('row', { name: /Rent/ });
    expect(within(rent).getByText('×1')).toBeInTheDocument();
  });

  it('multiplies the per-occurrence amount by the times it lands', async () => {
    renderWithProviders(<CategoriesPage />);

    // 60.00 a week x 5 = 300.00, not the 260.00 a 52/12 average would give.
    const groceries = await screen.findByRole('row', { name: /Groceries/ });
    expect(within(groceries).getByText('€300.00')).toBeInTheDocument();
  });

  it('says which dates the period covers, so a planned total can be explained', async () => {
    renderWithProviders(<CategoriesPage />);

    expect(
      await screen.findByText(/Counted across August 2026 \(2026-08-01 → 2026-08-31\)/),
    ).toBeInTheDocument();
  });

  it('summarises what is planned in, out and spare', async () => {
    renderWithProviders(<CategoriesPage />);

    // In: café 160 x 5 = 800.00, which is also the café row's own total, so
    // the assertion says which one it means.
    const summary = await screen.findByRole('list', { name: 'Plan summary' });
    expect(within(summary).getByText('€800.00')).toBeInTheDocument();
    expect(within(summary).getByText('€1,080.00')).toBeInTheDocument();
    // Spare is a balance, not a delta, so it uses format()'s hyphen rather
    // than formatSigned()'s true minus.
    expect(within(summary).getByText('-€280.00')).toBeInTheDocument();
  });
});

describe('CategoriesPage - editing the plan (BR-14)', () => {
  it('offers the planned amount as an editable field', async () => {
    renderWithProviders(<CategoriesPage />);

    expect(await screen.findByRole('textbox', { name: 'Planned amount for Rent' })).toHaveValue(
      '780.00',
    );
  });

  it('updates the period total as soon as an amount is changed', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    const field = await screen.findByRole('textbox', { name: 'Planned amount for Groceries' });
    await user.clear(field);
    await user.type(field, '80{Enter}');

    // 80.00 a week x 5 = 400.00, without waiting for the server.
    await waitFor(() => {
      const groceries = screen.getByRole('row', { name: /Groceries/ });
      expect(within(groceries).getByText('€400.00')).toBeInTheDocument();
    });
  });

  it('recounts the occurrences when the frequency changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    const frequency = await screen.findByRole('combobox', { name: 'Frequency for Groceries' });
    await user.selectOptions(frequency, 'MONTHLY');

    await waitFor(() => {
      const groceries = screen.getByRole('row', { name: /Groceries/ });
      expect(within(groceries).getByText('×1')).toBeInTheDocument();
    });
  });

  it('puts the old figure back when the save fails, rather than leaving one that was never stored', async () => {
    server.use(
      http.patch(`${API_BASE}/categories/:id`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Could not save', status: 500, detail: '' },
          { status: 500 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    const field = await screen.findByRole('textbox', { name: 'Planned amount for Groceries' });
    await user.clear(field);
    await user.type(field, '80{Enter}');

    await waitFor(() => {
      const groceries = screen.getByRole('row', { name: /Groceries/ });
      expect(within(groceries).getByText('€300.00')).toBeInTheDocument();
    });
  });
});
