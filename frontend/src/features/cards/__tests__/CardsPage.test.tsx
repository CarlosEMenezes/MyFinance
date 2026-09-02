import { screen, waitFor } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { CardsPage } from '../CardsPage';

describe('CardsPage', () => {
  it('titles the page', () => {
    renderWithProviders(<CardsPage />);

    expect(screen.getByRole('heading', { level: 1, name: 'Cards' })).toBeInTheDocument();
  });

  it('lists every card', async () => {
    renderWithProviders(<CardsPage />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Visa ·· 4417' })).toBeInTheDocument();
    });
    expect(screen.getByRole('heading', { name: 'Revolut debit' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'AIB debit' })).toBeInTheDocument();
  });

  it('puts credit cards first, since they are the ones with a bill to plan around', async () => {
    renderWithProviders(<CardsPage />);

    const names = (await screen.findAllByRole('heading', { level: 2 })).map(
      (heading) => heading.textContent,
    );

    expect(names[0]).toBe('Visa ·· 4417');
  });

  it('shows the credit card usage against its limit', async () => {
    renderWithProviders(<CardsPage />);

    await waitFor(() => {
      expect(screen.getByRole('progressbar', { name: /Visa ·· 4417/ })).toHaveAttribute(
        'aria-valuenow',
        '19',
      );
    });
  });

  it('states when a card purchase is really billed (BR-4)', async () => {
    renderWithProviders(<CardsPage />);

    expect(await screen.findByText(/up to day 25 is billed on 05-09-2026/)).toBeInTheDocument();
    expect(screen.getByText(/waits for the next bill \(05-10-2026\)/)).toBeInTheDocument();
  });

  it('says a debit card has no cycle rather than leaving it blank (BR-5)', async () => {
    renderWithProviders(<CardsPage />);

    expect(
      await screen.findByText(/Spend leaves Revolut Current the same day/),
    ).toBeInTheDocument();
  });

  it('shows no statement cycle for a debit card', async () => {
    renderWithProviders(<CardsPage />);

    await screen.findByRole('heading', { name: 'Visa ·· 4417' });

    // One progress bar, for the one credit card.
    expect(screen.getAllByRole('progressbar')).toHaveLength(1);
  });

  it('falls back to the debit presentation when a credit card arrives without its cycle', async () => {
    server.use(
      http.get(`${API_BASE}/cards`, () =>
        HttpResponse.json([
          {
            id: 'broken',
            name: 'Half-configured card',
            kind: 'CREDIT',
            accountId: 'revolut',
            settlesFrom: 'Revolut Current',
            creditLimit: null,
            currentBalance: null,
            closingDay: null,
            dueDay: null,
            cycle: null,
          },
        ]),
      ),
    );
    renderWithProviders(<CardsPage />);

    // Better a card with no cycle shown than a crash or an invented date.
    expect(
      await screen.findByRole('heading', { name: 'Half-configured card' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('says so when there are no cards', async () => {
    server.use(http.get(`${API_BASE}/cards`, () => HttpResponse.json([])));
    renderWithProviders(<CardsPage />);

    expect(await screen.findByRole('heading', { name: 'No cards yet' })).toBeInTheDocument();
  });

  it('explains a failure rather than rendering an empty page', async () => {
    server.use(
      http.get(`${API_BASE}/cards`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Cards are unavailable', status: 503, detail: '' },
          { status: 503 },
        ),
      ),
    );
    renderWithProviders(<CardsPage />);

    expect(await screen.findByText('Cards are unavailable')).toBeInTheDocument();
  });
});
