import { useState } from 'react';

import { CardSummary } from '../../components/CardSummary';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { fromIso } from '../../lib/dates';
import { fromMinorUnits } from '../../lib/money';
import type { Card } from '../../types/api';

import { NewCardDialog } from './NewCardDialog';
import styles from './CardsPage.module.css';
import { useCards, useCreateCard } from './hooks';
import { useAccounts } from '../accounts/hooks';

/**
 * Limits, closing and due days (BR-4, BR-5).
 *
 * The API's `Card` is one shape with nullable credit fields, because that is
 * what JSON can express. `CardSummary` is a discriminated union, because that
 * is what makes a debit card with a closing day unwriteable. This is the seam
 * between the two, and the only place the nulls are read.
 */
function toSummaryProps(card: Card) {
  if (
    card.kind === 'CREDIT' &&
    card.creditLimit !== null &&
    card.currentBalance !== null &&
    card.closingDay !== null &&
    card.dueDay !== null &&
    card.cycle !== null
  ) {
    return {
      kind: 'CREDIT',
      name: card.name,
      settlesFrom: card.settlesFrom,
      creditLimit: fromMinorUnits(card.creditLimit),
      currentBalance: fromMinorUnits(card.currentBalance),
      closingDay: card.closingDay,
      dueDay: card.dueDay,
      cycle: {
        nextBillDate: fromIso(card.cycle.nextBillDate),
        billDateOnClosingDay: fromIso(card.cycle.billDateOnClosingDay),
        billDateAfterClosingDay: fromIso(card.cycle.billDateAfterClosingDay),
      },
    } as const;
  }

  return { kind: 'DEBIT', name: card.name, settlesFrom: card.settlesFrom } as const;
}

export function CardsPage() {
  const { cards, isLoading, error } = useCards();
  const { accounts } = useAccounts();
  const [creating, setCreating] = useState(false);
  const create = useCreateCard();

  return (
    <>
      <PageHeader kicker="Fig. 06 — Limits, closing and due days" title="Cards">
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => {
            create.reset();
            setCreating(true);
          }}
        >
          + New card
        </button>
      </PageHeader>

      {isLoading && <p className={styles.status}>Loading cards…</p>}

      {error !== null && <EmptyState title="Cards could not be loaded" message={error.message} />}

      {!isLoading && error === null && cards.length === 0 && (
        <EmptyState
          title="No cards yet"
          message="Add a card to see when its spending is actually billed."
          action={
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setCreating(true);
              }}
            >
              + New card
            </button>
          }
        />
      )}

      <NewCardDialog
        open={creating}
        accounts={accounts}
        error={create.error?.message}
        onClose={() => {
          setCreating(false);
        }}
        onCreate={(body) => {
          create.mutate(body, {
            onSuccess: () => {
              setCreating(false);
            },
          });
        }}
      />

      {cards.length > 0 && (
        <ul className={styles.list}>
          {cards.map((card) => (
            <li key={card.id}>
              <CardSummary {...toSummaryProps(card)} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
