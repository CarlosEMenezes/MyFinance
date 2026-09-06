import { useState } from 'react';

import { AccountCard } from '../../components/AccountCard';
import { EmptyState } from '../../components/EmptyState';
import { MoneyText } from '../../components/MoneyText';
import { PageHeader } from '../../components/PageHeader';
import { fromMinorUnits } from '../../lib/money';

import { NewAccountDialog } from './NewAccountDialog';
import styles from './AccountsPage.module.css';
import { useAccounts, useCreateAccount } from './hooks';

/**
 * Where the money sits (BR-13).
 *
 * The total states when it has left accounts out, because a figure headed
 * "counted in totals" that silently omits an excluded account is the same
 * mistake BR-21 forbids for unconverted rows: a total that omits without
 * saying so is a wrong total.
 *
 * Pocket balances are never added in. They are already inside their parent
 * account, and `AccountCard` says as much where they are listed.
 */
export function AccountsPage() {
  const { accounts, countedTotal, hasExcludedAccounts, isLoading, error } = useAccounts();
  const [creating, setCreating] = useState(false);
  const create = useCreateAccount();

  return (
    <>
      <PageHeader kicker="Fig. 05 — Where the money sits" title="Accounts">
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => {
            create.reset();
            setCreating(true);
          }}
        >
          + New account
        </button>
      </PageHeader>

      {isLoading && <p className={styles.status}>Loading accounts…</p>}

      {error !== null && (
        <EmptyState title="Accounts could not be loaded" message={error.message} />
      )}

      {!isLoading && error === null && accounts.length === 0 && (
        <EmptyState
          title="No accounts yet"
          message="Add a cash, bank or savings account to start tracking where your money sits."
          action={
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setCreating(true);
              }}
            >
              + New account
            </button>
          }
        />
      )}

      <NewAccountDialog
        open={creating}
        accounts={accounts}
        error={create.error?.message}
        onClose={() => {
          setCreating(false);
        }}
        onCreate={(submission) => {
          create.mutate(submission, {
            // Left open on failure, with what was typed still in it: a form
            // that closes on an error has thrown the entry away.
            onSuccess: () => {
              setCreating(false);
            },
          });
        }}
      />

      {accounts.length > 0 && (
        <>
          <div className={styles.total}>
            <span className={styles.totalLabel}>Counted in totals</span>
            <span>
              <MoneyText amount={countedTotal} className={styles.totalValue} />
              {hasExcludedAccounts && (
                <span className={styles.excluded}> · some accounts are out of totals</span>
              )}
            </span>
          </div>

          <ul className={styles.list}>
            {accounts.map((account) => (
              <li key={account.id}>
                <AccountCard
                  name={account.name}
                  kind={account.kind}
                  balance={fromMinorUnits(account.balance)}
                  currency={account.currency}
                  includedInTotals={account.includeInTotals}
                  pockets={account.pockets.map((pocket) => ({
                    id: pocket.id,
                    name: pocket.name,
                    balance: fromMinorUnits(pocket.balance),
                  }))}
                  cards={account.cardNames}
                  {...(account.note === null ? {} : { note: account.note })}
                />
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}
