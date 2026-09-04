import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { API_BASE } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import type { UpdateUserRequest } from '../../../types/api';
import { SettingsPage } from '../SettingsPage';

const render = () => renderWithProviders(<SettingsPage />);

describe('SettingsPage - the profile', () => {
  it('titles the page', () => {
    render();

    expect(screen.getByRole('heading', { level: 1, name: 'Settings' })).toBeInTheDocument();
  });

  it('fills each field from the saved profile', async () => {
    render();

    expect(await screen.findByLabelText('Full name')).toHaveValue('Carlos Eduardo');
    expect(screen.getByLabelText('Age')).toHaveValue('24');
    expect(screen.getByLabelText('Role')).toHaveValue('Freelance designer');
    expect(screen.getByLabelText('Country')).toHaveValue('Ireland');
    expect(screen.getByLabelText('Pay cycle')).toHaveValue('IRREGULAR');
  });

  it('reads the saved profile back as a sentence', async () => {
    render();

    expect(
      await screen.findByText(/Carlos Eduardo, 24, Freelance designer — irregular pay cycle/),
    ).toBeInTheDocument();
  });

  it('saves a name once, when the field is left', async () => {
    let writes = 0;
    server.use(
      http.patch(`${API_BASE}/users/me`, async ({ request }) => {
        writes += 1;
        return HttpResponse.json((await request.json()) as UpdateUserRequest);
      }),
    );
    const user = userEvent.setup();
    render();

    const name = await screen.findByLabelText('Full name');
    await user.clear(name);
    await user.type(name, 'Ada');
    // Typing alone must not write: a half-typed name is not a name.
    expect(writes).toBe(0);

    await user.tab();
    await waitFor(() => {
      expect(writes).toBe(1);
    });
  });

  it('does not write when the field is left unchanged', async () => {
    let writes = 0;
    server.use(
      http.patch(`${API_BASE}/users/me`, () => {
        writes += 1;
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByLabelText('Full name'));
    await user.tab();

    expect(writes).toBe(0);
  });

  it('keeps a saved name after a refetch', async () => {
    const user = userEvent.setup();
    render();

    const name = await screen.findByLabelText('Full name');
    await user.clear(name);
    await user.type(name, 'Ada Lovelace');
    await user.tab();

    await waitFor(() => {
      expect(screen.getByLabelText('Full name')).toHaveValue('Ada Lovelace');
    });
    expect(screen.getByText(/Ada Lovelace, 24/)).toBeInTheDocument();
  });

  it('puts the name back when the write fails', async () => {
    server.use(
      http.patch(`${API_BASE}/users/me`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Could not save', status: 500, detail: '' },
          { status: 500 },
        ),
      ),
    );
    const user = userEvent.setup();
    render();

    const name = await screen.findByLabelText('Full name');
    await user.clear(name);
    await user.type(name, 'Ada');
    await user.tab();

    await waitFor(() => {
      expect(screen.getByLabelText('Full name')).toHaveValue('Carlos Eduardo');
    });
  });

  it('refuses an age that is not a number rather than saving something else', async () => {
    let writes = 0;
    server.use(
      http.patch(`${API_BASE}/users/me`, () => {
        writes += 1;
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    render();

    const age = await screen.findByLabelText('Age');
    await user.clear(age);
    await user.type(age, 'twenty');
    await user.tab();

    expect(writes).toBe(0);
    // The field says what is stored, not what was typed and refused.
    await waitFor(() => {
      expect(screen.getByLabelText('Age')).toHaveValue('24');
    });
  });

  it('clears the age when the field is emptied', async () => {
    const user = userEvent.setup();
    render();

    const age = await screen.findByLabelText('Age');
    await user.clear(age);
    await user.tab();

    await waitFor(() => {
      expect(screen.getByLabelText('Age')).toHaveValue('');
    });
    expect(screen.getByText(/Carlos Eduardo, Freelance designer/)).toBeInTheDocument();
  });

  it('stores a cleared role as nothing, not as an empty answer', async () => {
    let sent: Record<string, unknown> = {};
    server.use(
      http.patch(`${API_BASE}/users/me`, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(sent);
      }),
    );
    const user = userEvent.setup();
    render();

    await user.clear(await screen.findByLabelText('Role'));
    await user.tab();

    await waitFor(() => {
      expect(sent).toEqual({ role: null });
    });
  });

  it('saves a country', async () => {
    const user = userEvent.setup();
    render();

    const country = await screen.findByLabelText('Country');
    await user.clear(country);
    await user.type(country, 'Portugal');
    await user.tab();

    await waitFor(() => {
      expect(screen.getByLabelText('Country')).toHaveValue('Portugal');
    });
  });

  it('saves a pay cycle as soon as it is chosen', async () => {
    const user = userEvent.setup();
    render();

    await user.selectOptions(await screen.findByLabelText('Pay cycle'), 'WEEKLY');

    await waitFor(() => {
      expect(screen.getByText(/weekly pay cycle/)).toBeInTheDocument();
    });
  });

  it('says the settings could not be loaded rather than showing an empty form', async () => {
    server.use(
      http.get(`${API_BASE}/users/me`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Profile unavailable', status: 503, detail: '' },
          { status: 503 },
        ),
      ),
    );
    render();

    expect(await screen.findByText('Profile unavailable')).toBeInTheDocument();
  });
});

describe('SettingsPage - money and dates (BR-8)', () => {
  it('shows the default currency and says what it governs', async () => {
    render();

    expect(await screen.findByLabelText('Default currency')).toHaveValue('EUR');
    expect(screen.getByText(/Every total is stated in this/)).toBeInTheDocument();
  });

  it('changes the default currency', async () => {
    const user = userEvent.setup();
    render();

    await user.selectOptions(await screen.findByLabelText('Default currency'), 'GBP');

    await waitFor(() => {
      expect(screen.getByLabelText('Default currency')).toHaveValue('GBP');
    });
  });

  it('shows every rate in the direction the provider quoted it', async () => {
    render();

    expect(await screen.findByText('1 EUR = 1.0858 USD')).toBeInTheDocument();
    expect(screen.getByText('1 EUR = 0.8422 GBP')).toBeInTheDocument();
    // The base against itself is always 1 and says nothing.
    expect(screen.queryByText(/= 1.0000 EUR/)).not.toBeInTheDocument();
  });

  it('says when the rates were last pulled', async () => {
    render();

    expect(await screen.findByText('Last pulled 31-08-2026 08:12')).toBeInTheDocument();
  });

  it('states the rate stamp in the chosen date format', async () => {
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('radio', { name: 'YYYY-MM-DD' }));

    await waitFor(() => {
      expect(screen.getByText('Last pulled 2026-08-31 08:12')).toBeInTheDocument();
    });
  });

  it('says so when no rates have been fetched, rather than implying they are current', async () => {
    server.use(
      http.get(`${API_BASE}/fx/rates`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'No provider', status: 503, detail: '' },
          { status: 503 },
        ),
      ),
    );
    render();

    expect(await screen.findByText('No rates fetched yet.')).toBeInTheDocument();
  });

  it('shows which week start is chosen', async () => {
    render();

    expect(await screen.findByRole('radio', { name: 'Monday' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Sunday' })).not.toBeChecked();
  });

  it('changes the week start', async () => {
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('radio', { name: 'Sunday' }));

    await waitFor(() => {
      expect(screen.getByRole('radio', { name: 'Sunday' })).toBeChecked();
    });
  });
});

describe('SettingsPage - preferences', () => {
  it('shows which preferences are on', async () => {
    render();

    expect(
      await screen.findByRole('checkbox', { name: /Convert foreign amounts automatically/ }),
    ).toBeChecked();
    expect(
      screen.getByRole('checkbox', { name: /Carry unspent budget into the next period/ }),
    ).not.toBeChecked();
  });

  it('explains what each preference does', async () => {
    render();

    expect(
      await screen.findByText(/the entry keeps the currency it was logged in/),
    ).toBeInTheDocument();
  });

  it('turns a preference on', async () => {
    const user = userEvent.setup();
    render();

    await user.click(
      await screen.findByRole('checkbox', { name: /Carry unspent budget into the next period/ }),
    );

    await waitFor(() => {
      expect(
        screen.getByRole('checkbox', { name: /Carry unspent budget into the next period/ }),
      ).toBeChecked();
    });
  });

  it('changes only the preference that was clicked', async () => {
    const user = userEvent.setup();
    render();

    await user.click(
      await screen.findByRole('checkbox', { name: /Carry unspent budget into the next period/ }),
    );

    await waitFor(() => {
      expect(
        screen.getByRole('checkbox', { name: /Carry unspent budget into the next period/ }),
      ).toBeChecked();
    });
    expect(
      screen.getByRole('checkbox', { name: /Convert foreign amounts automatically/ }),
    ).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Round goal contributions up/ })).toBeChecked();
  });
});
