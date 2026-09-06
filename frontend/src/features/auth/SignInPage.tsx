import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { Field } from '../../components/Field';
import { Panel } from '../../components/Panel';

import styles from './SignInPage.module.css';
import { useLogin, useRegister } from './hooks';

/**
 * Signing in, and registering, which are the same form with one extra field.
 *
 * Two routes rather than a toggle, so each has its own address: arriving at a
 * bookmark, a password manager, or the back button all behave the way a person
 * expects them to.
 *
 * Nothing here handles a token. ADR-11 puts the session in an HttpOnly cookie,
 * so a successful submit simply navigates — the browser is already carrying
 * proof of who you are, and this code could not read it if it wanted to.
 */

export type SignInMode = 'LOGIN' | 'REGISTER';

export interface SignInPageProps {
  readonly mode: SignInMode;
}

/**
 * Long rather than complex. Length is what defeats guessing; character classes
 * mostly defeat the person choosing one, and push them toward "Password1!",
 * which is worse than four ordinary words. Matches the server's rule.
 */
const MINIMUM_PASSWORD_LENGTH = 12;

export function SignInPage({ mode }: SignInPageProps) {
  const registering = mode === 'REGISTER';
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [attempted, setAttempted] = useState(false);

  const login = useLogin();
  const register = useRegister();
  const pending = login.isPending || register.isPending;
  const failure = (registering ? register.error : login.error)?.message;

  const emailError =
    attempted && !email.includes('@') ? 'Enter the email address you signed up with.' : undefined;
  const passwordError =
    attempted && registering && password.length < MINIMUM_PASSWORD_LENGTH
      ? `Use at least ${String(MINIMUM_PASSWORD_LENGTH)} characters. A few ordinary words is easier to remember and harder to guess than one short, clever one.`
      : undefined;
  const nameError =
    attempted && registering && name.trim() === '' ? 'What should we call you?' : undefined;

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    setAttempted(true);

    if (!email.includes('@')) {
      return;
    }
    const arrive = {
      onSuccess: () => {
        // `replace`, so the back button does not return to a form that would
        // only bounce the person forward again.
        navigate('/', { replace: true });
      },
    };

    if (registering) {
      if (password.length < MINIMUM_PASSWORD_LENGTH || name.trim() === '') {
        return;
      }
      register.mutate({ email: email.trim(), password, name: name.trim() }, arrive);
      return;
    }
    if (password === '') {
      return;
    }
    login.mutate({ email: email.trim(), password }, arrive);
  };

  return (
    <main className={styles.page}>
      <div className={styles.frame}>
        <p className={styles.brand}>BUDGET TRACKER</p>
        <p className={styles.kicker}>
          {registering ? 'Fig. 00 — A new plan' : 'Fig. 00 — Where you left off'}
        </p>

        <Panel title={registering ? 'Create an account' : 'Sign in'}>
          <form onSubmit={submit} noValidate className={styles.form}>
            {failure !== undefined && (
              <p className={styles.error} role="alert">
                {failure}
              </p>
            )}

            {registering && (
              <Field label="Name" error={nameError}>
                {({ id, describedBy }) => (
                  <input
                    id={id}
                    className="input"
                    type="text"
                    value={name}
                    autoComplete="name"
                    aria-describedby={describedBy}
                    onChange={(event) => {
                      setName(event.target.value);
                    }}
                  />
                )}
              </Field>
            )}

            <Field label="Email" error={emailError}>
              {({ id, describedBy }) => (
                <input
                  id={id}
                  className="input"
                  type="email"
                  value={email}
                  autoComplete="username"
                  aria-describedby={describedBy}
                  onChange={(event) => {
                    setEmail(event.target.value);
                  }}
                />
              )}
            </Field>

            <Field label="Password" error={passwordError}>
              {({ id, describedBy }) => (
                <input
                  id={id}
                  className="input"
                  type="password"
                  value={password}
                  // Tells a password manager whether to offer a saved password
                  // or to generate one. Getting this wrong is why so many
                  // sign-up forms fight the browser.
                  autoComplete={registering ? 'new-password' : 'current-password'}
                  aria-describedby={describedBy}
                  onChange={(event) => {
                    setPassword(event.target.value);
                  }}
                />
              )}
            </Field>

            <button type="submit" className="btn btn-primary btn-block" disabled={pending}>
              {registering ? 'Create account' : 'Sign in'}
            </button>
          </form>
        </Panel>

        <p className={styles.swap}>
          {registering ? (
            <>
              Already have an account? <Link to="/login">Sign in</Link>
            </>
          ) : (
            <>
              No account yet? <Link to="/register">Create one</Link>
            </>
          )}
        </p>
      </div>
    </main>
  );
}
