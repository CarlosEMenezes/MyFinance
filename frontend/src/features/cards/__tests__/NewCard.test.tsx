import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { CardsPage } from '../CardsPage';

const render = () => renderWithProviders(<CardsPage />);

const openDialog = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(await screen.findByRole('button', { name: '+ New card' }));
  return screen.findByRole('dialog');
};

describe('CardsPage - creating a card', () => {
  it('offers a way to create one', async () => {
    render();

    expect(await screen.findByRole('button', { name: '+ New card' })).toBeInTheDocument();
  });

  it('creates a credit card and shows it in the list', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Amex Gold');
    await user.type(within(dialog).getByLabelText('Credit limit'), '3000');
    await user.click(within(dialog).getByRole('button', { name: 'Create card' }));

    expect(await screen.findByText('Amex Gold')).toBeInTheDocument();
  });

  it('sends the limit in minor units and the cycle days as numbers', async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(`${API_BASE}/cards`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'c-9', ...sent }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Amex Gold');
    await user.type(within(dialog).getByLabelText('Credit limit'), '3000.50');
    await user.click(within(dialog).getByRole('button', { name: 'Create card' }));

    await waitFor(() => {
      expect(sent).toMatchObject({
        kind: 'CREDIT',
        name: 'Amex Gold',
        creditLimit: 300050,
        closingDay: 25,
        dueDay: 5,
      });
    });
  });
});

describe('CardsPage - the statement cycle (BR-4, BR-5)', () => {
  it('refuses a closing day that does not exist in every month', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Amex');
    await user.type(within(dialog).getByLabelText('Credit limit'), '1000');
    await user.clear(within(dialog).getByLabelText('Closing day'));
    await user.type(within(dialog).getByLabelText('Closing day'), '31');
    await user.click(within(dialog).getByRole('button', { name: 'Create card' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Use a day from 1 to 28');
  });

  it('accepts day 28, the last that exists in every month', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Amex');
    await user.type(within(dialog).getByLabelText('Credit limit'), '1000');
    await user.clear(within(dialog).getByLabelText('Closing day'));
    await user.type(within(dialog).getByLabelText('Closing day'), '28');
    await user.click(within(dialog).getByRole('button', { name: 'Create card' }));

    expect(await screen.findByText('Amex')).toBeInTheDocument();
  });

  it('states where spending lands before the card is saved', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);

    // Due day 5 is before closing day 25, so the bill falls the month after.
    expect(within(dialog).getByText(/of the following month/)).toBeInTheDocument();
  });

  it('drops the month roll when the due day comes after closing', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.clear(within(dialog).getByLabelText('Payment due day'));
    await user.type(within(dialog).getByLabelText('Payment due day'), '28');

    expect(within(dialog).queryByText(/of the following month/)).not.toBeInTheDocument();
  });

  it('offers a debit card no cycle at all, rather than empty cycle fields', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    expect(within(dialog).getByLabelText('Closing day')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('radio', { name: 'Debit' }));

    expect(within(dialog).queryByLabelText('Closing day')).not.toBeInTheDocument();
    expect(within(dialog).queryByLabelText('Credit limit')).not.toBeInTheDocument();
    expect(within(dialog).getByText(/leaves the account the same day/)).toBeInTheDocument();
  });

  it('sends a debit card with no cycle fields at all', async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(`${API_BASE}/cards`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'c-9', ...sent }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.click(within(dialog).getByRole('radio', { name: 'Debit' }));
    await user.type(within(dialog).getByLabelText('Name'), 'N26 debit');
    await user.click(within(dialog).getByRole('button', { name: 'Create card' }));

    await waitFor(() => {
      expect(sent).toMatchObject({ kind: 'DEBIT', name: 'N26 debit' });
    });
    expect(sent).not.toHaveProperty('closingDay');
    expect(sent).not.toHaveProperty('creditLimit');
  });
});
