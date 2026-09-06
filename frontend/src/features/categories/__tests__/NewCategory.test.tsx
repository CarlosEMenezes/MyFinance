import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { CategoriesPage } from '../CategoriesPage';

const render = () => renderWithProviders(<CategoriesPage />);

const openDialog = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.click(await screen.findByRole('button', { name: '+ New category' }));
  return screen.findByRole('dialog');
};

describe('CategoriesPage - creating a category (BR-14)', () => {
  it('offers a way to create one', async () => {
    render();

    expect(await screen.findByRole('button', { name: '+ New category' })).toBeInTheDocument();
  });

  it('creates a category and shows it in the table', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Course fees');
    await user.type(within(dialog).getByLabelText('Planned amount'), '85');
    await user.click(within(dialog).getByRole('button', { name: 'Create category' }));

    expect(await screen.findByText('Course fees')).toBeInTheDocument();
  });

  it('creates the planned amount and its frequency with the category', async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(`${API_BASE}/categories`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'cat-9', archived: false, ...sent }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Course fees');
    await user.type(within(dialog).getByLabelText('Planned amount'), '85.50');
    await user.selectOptions(within(dialog).getByLabelText('Every'), 'WEEKLY');
    await user.click(within(dialog).getByRole('button', { name: 'Create category' }));

    // BR-14: the plan is not a second step. A category arrives with one.
    await waitFor(() => {
      expect(sent).toMatchObject({
        name: 'Course fees',
        type: 'EXPENSE',
        plannedAmount: 8550,
        plannedFrequency: 'WEEKLY',
      });
    });
    expect(sent.anchorDate).toEqual(expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));
  });

  it('names every field that is wrong, not just the first', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.click(within(dialog).getByRole('button', { name: 'Create category' }));

    // Fixing one field at a time and being refused again each time is worse
    // than being told what is missing.
    const alerts = await within(dialog).findAllByRole('alert');
    expect(alerts.map((alert) => alert.textContent)).toEqual([
      'Give the category a name.',
      'Enter a planned amount, such as 60.00.',
    ]);
  });

  it('refuses a category with no planned amount, which BR-14 does not allow', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.type(within(dialog).getByLabelText('Name'), 'Course fees');
    await user.click(within(dialog).getByRole('button', { name: 'Create category' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Enter a planned amount');
  });

  it('says why the anchor date matters', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);

    // BR-10 counts real dates, so this is not a cosmetic field.
    expect(within(dialog).getByText(/counted by real dates/)).toBeInTheDocument();
  });
});

describe('CategoriesPage - groups belong to a side', () => {
  it('offers expense groups for an expense', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    const group = within(dialog).getByLabelText('Group');

    expect(within(group).getByRole('option', { name: 'Fixed' })).toBeInTheDocument();
    expect(within(group).queryByRole('option', { name: 'Employment' })).not.toBeInTheDocument();
  });

  it('swaps the groups when the kind changes, rather than carrying one across', async () => {
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.selectOptions(within(dialog).getByLabelText('Group'), 'Debt');
    await user.click(within(dialog).getByRole('radio', { name: 'Earning' }));

    const group = within(dialog).getByLabelText('Group');
    expect(within(group).queryByRole('option', { name: 'Debt' })).not.toBeInTheDocument();
    // "Debt" is not an earnings group, so the choice falls back rather than
    // being sent as a group that side does not have.
    expect(group).toHaveValue('Employment');
  });

  it('sends the earning side when the kind is switched', async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.post(`${API_BASE}/categories`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 'cat-9', archived: false, ...sent }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    render();

    const dialog = await openDialog(user);
    await user.click(within(dialog).getByRole('radio', { name: 'Earning' }));
    await user.type(within(dialog).getByLabelText('Name'), 'Workshops');
    await user.type(within(dialog).getByLabelText('Planned amount'), '300');
    await user.click(within(dialog).getByRole('button', { name: 'Create category' }));

    await waitFor(() => {
      expect(sent).toMatchObject({ type: 'EARNING', group: 'Employment' });
    });
  });
});
