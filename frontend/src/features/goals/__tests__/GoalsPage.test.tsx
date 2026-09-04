import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { GoalsPage } from '../GoalsPage';

// The prototype's "today", fixed so the horizon a goal implies is stable.
// Only Date is faked: faking timers wholesale freezes the event loop and the
// MSW request never resolves.
beforeAll(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 7, 31));
});
afterAll(() => {
  vi.useRealTimers();
});

const render = () => renderWithProviders(<GoalsPage />);

describe('GoalsPage - the list', () => {
  it('titles the page', () => {
    render();

    expect(screen.getByRole('heading', { level: 1, name: 'Plan acquisition' })).toBeInTheDocument();
  });

  it('lists goals in rank order', async () => {
    render();

    await waitFor(() => {
      expect(screen.getByText('MacBook Air M4')).toBeInTheDocument();
    });
    const names = screen.getAllByRole('button').map((button) => button.textContent);
    expect(names[0]).toContain('MacBook Air M4');
    expect(names[1]).toContain('Emergency fund');
  });

  it('shows what is saved against the target', async () => {
    render();

    // The what-if panel repeats both figures, so the assertion says it means
    // the goal's own card.
    const card = (await screen.findByText('MacBook Air M4')).closest('button');
    expect(within(card as HTMLElement).getByText('€410.00')).toBeInTheDocument();
    expect(within(card as HTMLElement).getByText('€1,349.00')).toBeInTheDocument();
  });

  it('draws progress against a pace marker, and says what the marker means (BR-11)', async () => {
    render();

    const bar = await screen.findByRole('progressbar', { name: 'MacBook Air M4 progress' });
    expect(bar).toHaveAttribute('aria-valuenow', '30');
    expect(bar).toHaveAttribute('aria-valuetext', '30% saved, 67% expected by now');
  });

  it('says whether each goal is keeping up, in words as well as colour', async () => {
    render();

    const behind = (await screen.findByText('MacBook Air M4')).closest('button');
    expect(within(behind as HTMLElement).getByText('Behind')).toBeInTheDocument();

    const onPace = screen.getByText('Emergency fund').closest('button');
    expect(within(onPace as HTMLElement).getByText('On pace')).toBeInTheDocument();
  });

  it('states what each goal needs each month', async () => {
    render();

    const card = (await screen.findByText('MacBook Air M4')).closest('button');
    expect(within(card as HTMLElement).getByText('€234.75')).toBeInTheDocument();
  });
});

describe('GoalsPage - the what-if (BR-11)', () => {
  it('points at the first goal to begin with', async () => {
    render();

    expect(await screen.findByText(/What-if · MacBook Air M4/)).toBeInTheDocument();
  });

  it('marks which goal the what-if is about', async () => {
    render();

    const first = (await screen.findByText('MacBook Air M4')).closest('button');
    expect(first).toHaveAttribute('aria-pressed', 'true');
  });

  it('points at another goal when it is chosen', async () => {
    const user = userEvent.setup();
    render();
    await screen.findByText('Emergency fund');

    await user.click(screen.getByText('Emergency fund').closest('button') as HTMLElement);

    expect(screen.getByText(/What-if · Emergency fund/)).toBeInTheDocument();
  });

  it('opens on the horizon the goal already implies', async () => {
    render();

    // 20-12-2026 is a little under four months from 31-08-2026.
    expect(await screen.findByText(/4 months out/)).toBeInTheDocument();
  });

  it('judges affordability against what the whole plan leaves spare, not the goal', async () => {
    render();

    // The dashboard plans 430.86 spare a month; MacBook needs 234.75.
    expect(
      await screen.findByText(/Fits inside your current spare €430.86 a month/),
    ).toBeInTheDocument();
  });

  it('says nothing about goals it cannot load', async () => {
    server.use(
      http.get(`${API_BASE}/goals`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Goals are unavailable', status: 503, detail: '' },
          { status: 503 },
        ),
      ),
    );
    render();

    expect(await screen.findByText('Goals are unavailable')).toBeInTheDocument();
  });

  it('says so when there are no goals', async () => {
    server.use(http.get(`${API_BASE}/goals`, () => HttpResponse.json([])));
    render();

    expect(await screen.findByRole('heading', { name: 'No goals yet' })).toBeInTheDocument();
  });
});
