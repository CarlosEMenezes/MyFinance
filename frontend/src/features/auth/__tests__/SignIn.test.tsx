import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import App from '../../../App';
import { API_BASE, signOutInTests } from '../../../test/handlers';
import { renderWithProviders } from '../../../test/render';
import { server } from '../../../test/server';
import { SignInPage } from '../SignInPage';

const signedOut = (route = '/') => {
  signOutInTests();
  return renderWithProviders(<App />, { route });
};

describe('the guard (ADR-11)', () => {
  it('sends a signed-out visitor to the login page', async () => {
    signedOut('/accounts');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('shows no navigation at all until someone is signed in', async () => {
    signedOut('/accounts');

    await screen.findByRole('heading', { name: 'Sign in' });
    // A shell around a login form would be a promise the page cannot keep, and
    // the pages behind it would each fire a request nobody is entitled to make.
    expect(
      screen.queryByRole('navigation', { name: 'Main', hidden: true }),
    ).not.toBeInTheDocument();
  });

  it('lets a signed-in visitor through to the app', async () => {
    renderWithProviders(<App />, { route: '/accounts' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Accounts' })).toBeInTheDocument();
  });

  it('does not offer the login page to someone already signed in', async () => {
    renderWithProviders(<App />, { route: '/login' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Overview' })).toBeInTheDocument();
  });
});

describe('signing in', () => {
  it('arrives at the app on success', async () => {
    const user = userEvent.setup();
    signedOut('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'a-long-enough-passphrase');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Overview' })).toBeInTheDocument();
  });

  it('says what went wrong without saying which half was wrong', async () => {
    const user = userEvent.setup();
    signedOut('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    // "No account with that email" would tell whoever asked that an address is
    // not registered — information about a person they did not have.
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That email address and password do not match',
    );
  });

  it('stays on the form when the sign-in fails', async () => {
    const user = userEvent.setup();
    signedOut('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await screen.findByRole('alert');
    // The email survives, because retyping it is a punishment for a typo in the
    // other field.
    expect(screen.getByLabelText('Email')).toHaveValue('ada@example.com');
  });

  it('refuses to submit without an email', async () => {
    let attempts = 0;
    server.use(
      http.post(`${API_BASE}/auth/login`, () => {
        attempts += 1;
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    signedOut('/login');

    await user.click(await screen.findByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Enter the email address');
    expect(attempts).toBe(0);
  });

  it('never puts a token anywhere this code could read', async () => {
    const user = userEvent.setup();
    signedOut('/login');

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'a-long-enough-passphrase');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByRole('heading', { level: 1, name: 'Overview' });

    // ADR-11: the session is an HttpOnly cookie. A token in localStorage is a
    // token any injected script can take.
    expect(Object.keys(window.localStorage)).toHaveLength(0);
    expect(Object.keys(window.sessionStorage)).toHaveLength(0);
  });
});

describe('registering', () => {
  it('offers a way to create an account from the login page', async () => {
    const user = userEvent.setup();
    signedOut('/login');

    await user.click(await screen.findByRole('link', { name: 'Create one' }));

    expect(await screen.findByRole('heading', { name: 'Create an account' })).toBeInTheDocument();
  });

  it('asks for a name, which signing in does not', async () => {
    signedOut('/register');

    expect(await screen.findByLabelText('Name')).toBeInTheDocument();
  });

  it('signs the new account straight in', async () => {
    const user = userEvent.setup();
    signedOut('/register');

    await user.type(await screen.findByLabelText('Name'), 'Ada Lovelace');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'a-long-enough-passphrase');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    // Registering and then being asked to sign in is a step that exists only
    // because the implementation found it convenient.
    expect(await screen.findByRole('heading', { level: 1, name: 'Overview' })).toBeInTheDocument();
  });

  it('asks for a long password rather than a fiddly one', async () => {
    const user = userEvent.setup();
    signedOut('/register');

    await user.type(await screen.findByLabelText('Name'), 'Ada');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('at least 12 characters');
  });

  it('tells the password manager which kind of field this is', async () => {
    signedOut('/register');

    // Getting this wrong is why so many sign-up forms fight the browser.
    expect(await screen.findByLabelText('Password')).toHaveAttribute(
      'autocomplete',
      'new-password',
    );
  });
});

describe('signing out', () => {
  it('returns to the login page', async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { route: '/' });

    await screen.findByRole('heading', { level: 1, name: 'Overview' });
    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    // Signing out clears every cached query, so several refetches settle before
    // the guard sees the 401. Longer than the default, and still not slow.
    expect(
      await screen.findByRole('heading', { name: 'Sign in' }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });
});

describe('SignInPage on its own', () => {
  it('names itself so a bookmark reads sensibly', () => {
    renderWithProviders(<SignInPage mode="LOGIN" />);

    const frame = screen.getByRole('main');
    expect(within(frame).getByText('BUDGET TRACKER')).toBeInTheDocument();
  });

  it('offers the way back to signing in from registering', async () => {
    renderWithProviders(<SignInPage mode="REGISTER" />);

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
    });
  });
});
