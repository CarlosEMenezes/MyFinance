import { screen, waitFor, within } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { dashboard } from '../../../test/dashboard.fixture';
import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { OverviewPage } from '../OverviewPage';

describe('OverviewPage - the position (BR-1, BR-2)', () => {
  it('titles the page with the period it covers', async () => {
    renderWithProviders(<OverviewPage />);

    await waitFor(() => {
      expect(screen.getByText(/Fig. 01 — Position, August 2026/)).toBeInTheDocument();
    });
  });

  it('shows the total position, negative when more is owed than held', async () => {
    renderWithProviders(<OverviewPage />);

    // 2,412.30 available against 3,767.88 owed. BR-1 allows this to be negative.
    expect(await screen.findByText('-€1,355.58')).toBeInTheDocument();
  });

  it('marks a negative position as over plan rather than leaving it neutral', async () => {
    renderWithProviders(<OverviewPage />);

    const total = await screen.findByText('-€1,355.58');
    expect(total.parentElement?.className).toMatch(/negative/);
  });

  it('shows what is available and what is owed as separate figures', async () => {
    renderWithProviders(<OverviewPage />);

    expect(await screen.findByText('€2,412.30')).toBeInTheDocument();
    expect(screen.getByText('€3,767.88')).toBeInTheDocument();
  });

  it('breaks down what the debt is made of', async () => {
    renderWithProviders(<OverviewPage />);

    expect(
      await screen.findByText(/card €386.40 · instalments €831.88 · loans €2,549.60/),
    ).toBeInTheDocument();
  });

  it('says the money is cash rather than borrowed when nothing has been borrowed (BR-2)', async () => {
    renderWithProviders(<OverviewPage />);

    expect(await screen.findByText('cash + bank + savings')).toBeInTheDocument();
  });

  it('says how much of the available money is borrowed when some is (BR-2)', async () => {
    server.use(
      http.get(`${API_BASE}/dashboard`, () =>
        HttpResponse.json({
          ...dashboard,
          position: { ...dashboard.position, borrowed: 250000 },
        }),
      ),
    );
    renderWithProviders(<OverviewPage />);

    // Borrowing raises what is available and what is owed together, so the
    // available figure has to say part of it is not yours.
    expect(await screen.findByText('includes €2,500.00 borrowed')).toBeInTheDocument();
  });
});

describe('OverviewPage - the breakdown (BR-9)', () => {
  it('heads the earnings and expenses sections of one reckoning', async () => {
    renderWithProviders(<OverviewPage />);

    expect(await screen.findByRole('rowheader', { name: 'Earnings' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Expenses' })).toBeInTheDocument();
  });

  it('foots the table with the net for the period', async () => {
    renderWithProviders(<OverviewPage />);

    const net = await screen.findByRole('row', { name: /Net for period/ });
    expect(within(net).getByText('€430.86')).toBeInTheDocument();
    expect(within(net).getByText('€244.78')).toBeInTheDocument();
  });

  it('colours each row by its own kind, in one table', async () => {
    renderWithProviders(<OverviewPage />);

    // Earning under plan is bad; spending under plan is good.
    const earning = await screen.findByRole('row', { name: /Freelance design/ });
    expect(within(earning).getByText('−€190.00').className).toMatch(/bad/);

    const expense = screen.getByRole('row', { name: /Transport/ });
    expect(within(expense).getByText('−€30.80').className).toMatch(/good/);
  });
});

describe('OverviewPage - what is coming and where it went', () => {
  it('lists what is due next with the direction of the money', async () => {
    renderWithProviders(<OverviewPage />);

    // Rent is both a category and an upcoming payment, so the assertion
    // says which it means.
    const upcoming = await screen.findByRole('list', { name: 'Upcoming payments' });
    expect(within(upcoming).getByText('Rent')).toBeInTheDocument();
    expect(within(upcoming).getByText('−€780.00')).toBeInTheDocument();
    expect(within(upcoming).getByText('+€168.00')).toBeInTheDocument();
  });

  it('draws real spend against the plan as a marker', async () => {
    renderWithProviders(<OverviewPage />);

    const bar = await screen.findByRole('progressbar', { name: /Groceries spend against plan/ });
    expect(bar).toHaveAttribute('aria-valuenow', '41');
    expect(bar).toHaveAttribute('aria-valuetext', '41% saved, 38% expected by now');
  });

  it('shows the accounts the position is built from', async () => {
    renderWithProviders(<OverviewPage />);

    expect(await screen.findByRole('heading', { name: 'Wallet' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'AIB Savings' })).toBeInTheDocument();
  });
});

describe('OverviewPage - failure', () => {
  it('explains a failure rather than rendering an empty position', async () => {
    server.use(
      http.get(`${API_BASE}/dashboard`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Overview is unavailable', status: 503, detail: '' },
          { status: 503 },
        ),
      ),
    );
    renderWithProviders(<OverviewPage />);

    expect(await screen.findByText('Overview is unavailable')).toBeInTheDocument();
  });
});
