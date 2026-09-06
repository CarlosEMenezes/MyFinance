import { useState } from 'react';

import { Checkbox } from '../../components/Checkbox';
import { Dialog } from '../../components/Dialog';
import { Field } from '../../components/Field';
import { SegmentedControl } from '../../components/SegmentedControl';
import { ZERO, toMinorUnits, tryFromDecimal } from '../../lib/money';
import type { Account, AccountKind, CurrencyCode } from '../../types/api';

import styles from './NewAccountDialog.module.css';

/**
 * Creating a place for money to sit, or a pocket inside one (BR-13).
 *
 * Pocket is offered here as a fourth "kind" because that is how a person
 * thinks about it — another named balance — but it is a different write, to a
 * different endpoint, against a parent account. BR-13 is the reason: a pocket
 * is already part of its parent, so it cannot be created free-floating and its
 * balance must never be added to the parent's.
 */

type NewAccountKind = AccountKind | 'POCKET';

const KINDS: readonly { readonly value: NewAccountKind; readonly label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK', label: 'Bank' },
  { value: 'SAVINGS', label: 'Savings' },
  { value: 'POCKET', label: 'Pocket' },
];

const CURRENCIES: readonly CurrencyCode[] = ['EUR', 'USD', 'GBP', 'BRL'];

export interface NewAccountSubmission {
  readonly name: string;
  readonly kind: NewAccountKind;
  readonly balance: number;
  readonly currency: CurrencyCode;
  readonly includeInTotals: boolean;
  /** Set only when `kind` is `POCKET`. */
  readonly parentAccountId: string | null;
}

export interface NewAccountDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly onCreate: (account: NewAccountSubmission) => void;
  /** Somewhere for a pocket to sit. */
  readonly accounts: readonly Account[];
  readonly defaultCurrency?: CurrencyCode;
  /** A failed write, so the dialog stays open with the entry intact. */
  readonly error?: string | undefined;
}

export function NewAccountDialog({
  open,
  onClose,
  onCreate,
  accounts,
  defaultCurrency = 'EUR',
  error,
}: NewAccountDialogProps) {
  const [name, setName] = useState('');
  const [kind, setKind] = useState<NewAccountKind>('BANK');
  const [balance, setBalance] = useState('');
  const [currency, setCurrency] = useState<CurrencyCode>(defaultCurrency);
  const [includeInTotals, setIncludeInTotals] = useState(true);
  // An override, not a value. `accounts` is empty on the first render and
  // arrives with the query, so seeding state from it once would leave the
  // select showing options while holding none of them.
  const [parentOverride, setParentOverride] = useState<string | null>(null);
  const [attempted, setAttempted] = useState(false);

  const isPocket = kind === 'POCKET';
  // An empty balance means zero, which is a real answer for a new account.
  const parsedBalance = balance.trim() === '' ? ZERO : tryFromDecimal(balance);

  const nameError = attempted && name.trim() === '' ? 'Give the account a name.' : undefined;
  const balanceError =
    attempted && parsedBalance === null ? 'Enter an amount, such as 120.50.' : undefined;
  const parentId = parentOverride ?? accounts[0]?.id ?? '';
  const parentError =
    attempted && isPocket && parentId === '' ? 'Choose the account it sits inside.' : undefined;

  const submit = () => {
    setAttempted(true);
    if (name.trim() === '' || parsedBalance === null || (isPocket && parentId === '')) {
      return;
    }
    onCreate({
      name: name.trim(),
      kind,
      balance: toMinorUnits(parsedBalance),
      currency,
      includeInTotals,
      parentAccountId: isPocket ? parentId : null,
    });
  };

  return (
    <Dialog
      open={open}
      title="New account or pocket"
      onClose={onClose}
      actions={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn-primary" onClick={submit}>
            {isPocket ? 'Create pocket' : 'Create account'}
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
            placeholder="e.g. Revolut Current"
            aria-describedby={describedBy}
            onChange={(event) => {
              setName(event.target.value);
            }}
          />
        )}
      </Field>

      <SegmentedControl
        name="account-kind"
        label="Type"
        options={KINDS}
        value={kind}
        onChange={setKind}
      />

      <div className={styles.pair}>
        <Field label={isPocket ? 'Amount set aside' : 'Opening balance'} error={balanceError}>
          {({ id, describedBy }) => (
            <input
              id={id}
              className="input"
              type="text"
              inputMode="decimal"
              value={balance}
              placeholder="0.00"
              aria-describedby={describedBy}
              onChange={(event) => {
                setBalance(event.target.value);
              }}
            />
          )}
        </Field>

        <Field label="Currency">
          {({ id }) => (
            <select
              id={id}
              className="input"
              value={currency}
              onChange={(event) => {
                setCurrency(event.target.value as CurrencyCode);
              }}
            >
              {CURRENCIES.map((code) => (
                <option key={code} value={code}>
                  {code}
                </option>
              ))}
            </select>
          )}
        </Field>
      </div>

      {isPocket ? (
        <>
          <Field
            label="Sits inside"
            hint="A pocket is part of its account's balance, so it is never counted twice."
            error={parentError}
          >
            {({ id, describedBy }) => (
              <select
                id={id}
                className="input"
                value={parentId}
                aria-describedby={describedBy}
                onChange={(event) => {
                  setParentOverride(event.target.value);
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
        </>
      ) : (
        <Checkbox
          label='Count in "total money now"'
          hint="Turn this off for an account you do not want in the money-now total."
          checked={includeInTotals}
          onChange={setIncludeInTotals}
        />
      )}
    </Dialog>
  );
}
