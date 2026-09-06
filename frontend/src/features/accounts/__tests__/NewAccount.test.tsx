import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { AccountsPage } from '../AccountsPage';

const render = () => renderWithProviders(<AccountsPage />);

const openDialog = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(await screen.findByRole('button', { name: '+ New account' }));
  return screen.findByRole('dialog');
};

describe('AccountsPage - creating an account', () => {
  it('offers a way to create one', async () => {
    render();

    expect(await screen.findByRole('button', { name: '+ New account' })).toBeInTheDocument();
  });

  it('creates an account and shows it in the list', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'N26 Current');
    await user.type(within(dialog).getByLabelText('Opening balance'), '250.50');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('N26 Current')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('sends the balance in minor units, parsed as text', async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(`${API_BASE}/accounts`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          { ...sent, id: 'a-9', pockets: [], cardNames: [] },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Jar');
    await user.type(within(dialog).getByLabelText('Opening balance'), '0.005');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    // Half away from zero, agreeing with the backend's HALF_UP.
    await waitFor(() => {
      expect(sent).toMatchObject({ name: 'Jar', balance: 1, kind: 'BANK', includeInTotals: true });
    });
  });

  it('treats an empty balance as zero rather than refusing it', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Empty jar');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Empty jar')).toBeInTheDocument();
  });

  it('refuses a nameless account and says why', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Give the account a name');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('refuses an amount that is not an amount', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Jar');
    await user.type(within(dialog).getByLabelText('Opening balance'), '12.');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Enter an amount');
  });

  it('keeps the entry when the write fails, and says what went wrong', async () => {
    server.use(
      http.post(`${API_BASE}/accounts`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Name already used', status: 409, detail: '' },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Wallet');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await within(dialog).findByText('Name already used')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Wallet');
  });
});

describe('AccountsPage - creating a pocket (BR-13)', () => {
  it('asks which account a pocket sits inside', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.click(within(dialog).getByRole('radio', { name: 'Pocket' }));

    expect(within(dialog).getByLabelText('Sits inside')).toBeInTheDocument();
    // The one dangerous misreading, said outright.
    expect(within(dialog).getByText(/never counted twice/)).toBeInTheDocument();
  });

  it('writes a pocket against its parent, not beside it', async () => {
    let path = '';
    server.use(
      http.post(`${API_BASE}/accounts/:id/pockets`, ({ params }) => {
        path = String(params.id);
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.click(within(dialog).getByRole('radio', { name: 'Pocket' }));
    await user.type(within(dialog).getByLabelText('Name'), 'New bike');
    await user.selectOptions(within(dialog).getByLabelText('Sits inside'), 'aib-savings');
    await user.click(within(dialog).getByRole('button', { name: 'Create pocket' }));

    await waitFor(() => {
      expect(path).toBe('aib-savings');
    });
  });

  it('leaves the parent balance alone, because the pocket is already in it', async () => {
    const user = userEvent.setup();
    render();

    const before = (await screen.findByText('Counted in totals')).parentElement?.textContent;

    const dialog = await openDialog(user);
    await user.click(within(dialog).getByRole('radio', { name: 'Pocket' }));
    await user.type(within(dialog).getByLabelText('Name'), 'New bike');
    await user.type(within(dialog).getByLabelText('Amount set aside'), '100');
    await user.click(within(dialog).getByRole('button', { name: 'Create pocket' }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(screen.getByText('Counted in totals').parentElement?.textContent).toBe(before);
  });

  it('does not offer the totals switch for a pocket, which has no say in it', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    expect(within(dialog).getByRole('checkbox', { name: /Count in/ })).toBeInTheDocument();

    await user.click(within(dialog).getByRole('radio', { name: 'Pocket' }));
    expect(within(dialog).queryByRole('checkbox', { name: /Count in/ })).not.toBeInTheDocument();
  });
});
