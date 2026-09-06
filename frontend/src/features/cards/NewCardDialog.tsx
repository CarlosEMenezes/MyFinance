import { useState } from 'react';

import { Dialog } from '../../components/Dialog';
import { Field } from '../../components/Field';
import { SegmentedControl } from '../../components/SegmentedControl';
import { toMinorUnits, tryFromDecimal } from '../../lib/money';
import type { Account, CardKind, CreateCardRequest } from '../../types/api';

import styles from './NewCardDialog.module.css';

/**
 * Creating a card (BR-4, BR-5).
 *
 * A debit card is not a credit card with empty fields: it has no cycle at all,
 * so choosing DEBIT removes the limit and the two cycle days rather than
 * disabling them. `CreateCardRequest` is a discriminated union for the same
 * reason — a debit card carrying a closing day cannot be expressed.
 *
 * The cycle days are validated to 1-28 here as well as on the server. BR-4
 * needs both to exist on every month, and 29, 30 and 31 do not. Saying so at
 * the field is kinder than a round-trip that comes back rejected.
 */

const KINDS: readonly { readonly value: CardKind; readonly label: string }[] = [
  { value: 'CREDIT', label: 'Credit' },
  { value: 'DEBIT', label: 'Debit' },
];

const FIRST_CYCLE_DAY = 1;
/** BR-4: the last day that exists in every month, February included. */
const LAST_CYCLE_DAY = 28;

export interface NewCardDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly onCreate: (card: CreateCardRequest) => void;
  /** A card settles from an account, so there must be one to settle from. */
  readonly accounts: readonly Account[];
  readonly error?: string | undefined;
}

function parseCycleDay(text: string): number | null {
  if (!/^\d{1,2}$/.test(text.trim())) {
    return null;
  }
  const day = Number(text.trim());
  return day >= FIRST_CYCLE_DAY && day <= LAST_CYCLE_DAY ? day : null;
}

const CYCLE_DAY_ERROR = `Use a day from ${String(FIRST_CYCLE_DAY)} to ${String(LAST_CYCLE_DAY)} — later days do not exist in every month.`;

export function NewCardDialog({ open, onClose, onCreate, accounts, error }: NewCardDialogProps) {
  const [name, setName] = useState('');
  const [kind, setKind] = useState<CardKind>('CREDIT');
  const [accountOverride, setAccountOverride] = useState<string | null>(null);
  const [limit, setLimit] = useState('');
  const [closingDay, setClosingDay] = useState('25');
  const [dueDay, setDueDay] = useState('5');
  const [attempted, setAttempted] = useState(false);

  const isCredit = kind === 'CREDIT';
  const accountId = accountOverride ?? accounts[0]?.id ?? '';
  const parsedLimit = tryFromDecimal(limit);
  const parsedClosing = parseCycleDay(closingDay);
  const parsedDue = parseCycleDay(dueDay);

  const nameError = attempted && name.trim() === '' ? 'Give the card a name.' : undefined;
  const accountError =
    attempted && accountId === '' ? 'Choose the account it settles from.' : undefined;
  const limitError =
    attempted && isCredit && parsedLimit === null ? 'Enter the limit, such as 2000.00.' : undefined;
  const closingError =
    attempted && isCredit && parsedClosing === null ? CYCLE_DAY_ERROR : undefined;
  const dueError = attempted && isCredit && parsedDue === null ? CYCLE_DAY_ERROR : undefined;

  const submit = () => {
    setAttempted(true);
    if (name.trim() === '' || accountId === '') {
      return;
    }
    if (!isCredit) {
      onCreate({ kind: 'DEBIT', name: name.trim(), accountId });
      return;
    }
    if (parsedLimit === null || parsedClosing === null || parsedDue === null) {
      return;
    }
    onCreate({
      kind: 'CREDIT',
      name: name.trim(),
      accountId,
      creditLimit: toMinorUnits(parsedLimit),
      closingDay: parsedClosing,
      dueDay: parsedDue,
    });
  };

  return (
    <Dialog
      open={open}
      title="New card"
      onClose={onClose}
      actions={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn-primary" onClick={submit}>
            Create card
          </button>
        </>
      }
    >
      {error !== undefined && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}

      <Field label="Name" error={nameError}>
        {({ id, describedBy }) => (
          <input
            id={id}
            className="input"
            type="text"
            value={name}
            placeholder="e.g. Visa ·· 4417"
            aria-describedby={describedBy}
            onChange={(event) => {
              setName(event.target.value);
            }}
          />
        )}
      </Field>

      <SegmentedControl
        name="card-kind"
        label="Kind"
        options={KINDS}
        value={kind}
        onChange={setKind}
      />

      <Field label="Settles from" error={accountError}>
        {({ id, describedBy }) => (
          <select
            id={id}
            className="input"
            value={accountId}
            aria-describedby={describedBy}
            onChange={(event) => {
              setAccountOverride(event.target.value);
            }}
          >
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </select>
        )}
      </Field>

      {isCredit ? (
        <>
          <Field label="Credit limit" error={limitError}>
            {({ id, describedBy }) => (
              <input
                id={id}
                className="input"
                type="text"
                inputMode="decimal"
                value={limit}
                placeholder="2000.00"
                aria-describedby={describedBy}
                onChange={(event) => {
                  setLimit(event.target.value);
                }}
              />
            )}
          </Field>

          <div className={styles.pair}>
            <Field label="Closing day" error={closingError}>
              {({ id, describedBy }) => (
                <input
                  id={id}
                  className="input"
                  type="text"
                  inputMode="numeric"
                  value={closingDay}
                  aria-describedby={describedBy}
                  onChange={(event) => {
                    setClosingDay(event.target.value);
                  }}
                />
              )}
            </Field>

            <Field label="Payment due day" error={dueError}>
              {({ id, describedBy }) => (
                <input
                  id={id}
                  className="input"
                  type="text"
                  inputMode="numeric"
                  value={dueDay}
                  aria-describedby={describedBy}
                  onChange={(event) => {
                    setDueDay(event.target.value);
                  }}
                />
              )}
            </Field>
          </div>

          <p className={styles.cycleNote}>
            {parsedClosing === null || parsedDue === null
              ? 'Set both days and this will say when spending lands on a bill.'
              : `Spending on or before day ${String(parsedClosing)} is billed that month; anything after it waits for the next statement. The bill falls due on day ${String(parsedDue)}${parsedDue <= parsedClosing ? ' of the following month' : ''}.`}
          </p>
        </>
      ) : (
        <p className={styles.cycleNote}>
          A debit card has no statement cycle — spending leaves the account the same day.
        </p>
      )}
    </Dialog>
  );
}
