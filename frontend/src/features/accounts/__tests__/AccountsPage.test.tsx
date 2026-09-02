import { screen, waitFor, within } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '../../../test/render';
import { API_BASE } from '../../../test/handlers';
import { server } from '../../../test/server';
import { AccountsPage } from '../AccountsPage';

describe('AccountsPage', () => {
  it('titles the page', async () => {
    renderWithProviders(<AccountsPage />);

    expect(screen.getByRole('heading', { level: 1, name: 'Accounts' })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Wallet' })).toBeInTheDocument();
    });
  });

  it('lists every account with its balance', async () => {
    renderWithProviders(<AccountsPage />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'AIB Savings' })).toBeInTheDocument();
    });
    expect(screen.getByText('\u20ac120.00')).toBeInTheDocument();
    expect(screen.getByText('\u20ac842.30')).toBeInTheDocument();
    expect(screen.getByText('\u20ac1,450.00')).toBeInTheDocument();
  });

  it('shows the pockets inside an account without adding them to it (BR-13)', async () => {
    renderWithProviders(<AccountsPage />);

    const pockets = await screen.findByRole('list', {
      name: /Pockets inside AIB Savings — already part of its balance/,
    });

    expect(within(pockets).getAllByRole('listitem')).toHaveLength(3);
    expect(within(pockets).getByText('MacBook Air M4')).toBeInTheDocument();
  });

  it('totals only the accounts that count, and never the pockets', async () => {
    renderWithProviders(<AccountsPage />);

    // 120.00 + 842.30 + 1,450.00 = 2,412.30. Adding the three pockets would
    // give 3,542.30, which is the mistake BR-13 exists to prevent.
    expect(await screen.findByText('\u20ac2,412.30')).toBeInTheDocument();
  });

  it('names the cards that settle from an account', async () => {
    renderWithProviders(<AccountsPage />);

    expect(await screen.findByText(/Visa ·· 4417 · Revolut debit/)).toBeInTheDocument();
  });

  it('leaves an excluded account out of the total and says so (BR-13)', async () => {
    server.use(
      http.get(`${API_BASE}/accounts`, () =>
        HttpResponse.json([
          {
            id: 'wallet',
            name: 'Wallet',
            kind: 'CASH',
            balance: 12000,
            currency: 'EUR',
            includeInTotals: true,
            note: null,
            pockets: [],
            cardNames: [],
          },
          {
            id: 'revolut',
            name: 'Revolut Current',
            kind: 'BANK',
            balance: 84230,
            currency: 'EUR',
            includeInTotals: true,
            note: null,
            pockets: [],
            cardNames: [],
          },
          {
            id: 'old',
            name: 'Old current account',
            kind: 'BANK',
            balance: 50000,
            currency: 'EUR',
            includeInTotals: false,
            note: null,
            pockets: [],
            cardNames: [],
          },
        ]),
      ),
    );
    renderWithProviders(<AccountsPage />);

    // 120.00 + 842.30 = 962.30. The 500.00 account is on screen but not in
    // the total, and the total says it is not the whole picture.
    expect(await screen.findByText('€962.30')).toBeInTheDocument();
    expect(screen.getByText('€500.00')).toBeInTheDocument();
    expect(screen.getByText(/some accounts are out of totals/)).toBeInTheDocument();
    expect(screen.getByText('out of totals')).toBeInTheDocument();
  });

  it('says so when there are no accounts at all', async () => {
    server.use(http.get(`${API_BASE}/accounts`, () => HttpResponse.json([])));
    renderWithProviders(<AccountsPage />);

    expect(await screen.findByRole('heading', { name: 'No accounts yet' })).toBeInTheDocument();
  });

  it('explains a failure rather than rendering an empty page', async () => {
    server.use(
      http.get(`${API_BASE}/accounts`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Accounts are unavailable', status: 503, detail: '' },
          { status: 503 },
        ),
      ),
    );
    renderWithProviders(<AccountsPage />);

    expect(
      await screen.findByRole('heading', { name: 'Accounts could not be loaded' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Accounts are unavailable')).toBeInTheDocument();
  });
});
