import { MoneyText } from '../MoneyText';
import { Panel } from '../Panel';
import { TagChip } from '../TagChip';

import styles from './AccountCard.module.css';
import type { AccountCardProps, AccountKind } from './AccountCard.types';

/**
 * One account, its balance, and anything nested inside it.
 *
 * BR-13 has two halves and both are visible here. An account excluded from the
 * totals is labelled wherever it appears, so a balance can never be read as
 * counting when it does not. And a pocket is a sub-balance *already inside* the
 * account: the list is introduced as such, and indented under a rule, because
 * the one dangerous misreading is treating pockets as money on top.
 */

const KIND_LABELS: Record<AccountKind, string> = {
  CASH: 'Cash',
  BANK: 'Bank account',
  SAVINGS: 'Savings',
};

export function AccountCard({
  name,
  kind,
  balance,
  currency = 'EUR',
  note,
  includedInTotals = true,
  pockets = [],
  cards = [],
}: AccountCardProps) {
  const balanceTone = kind === 'SAVINGS' ? styles.savings : undefined;

  return (
    <Panel>
      <div className={styles.header}>
        <div className={styles.identity}>
          <h2 className={styles.name}>{name}</h2>
          <TagChip variant={kind === 'SAVINGS' ? 'accent' : 'neutral'}>{KIND_LABELS[kind]}</TagChip>
          {!includedInTotals && <TagChip variant="outline">out of totals</TagChip>}
        </div>
        <MoneyText
          amount={balance}
          currency={currency}
          className={[styles.balance, balanceTone].filter(Boolean).join(' ')}
        />
      </div>

      {note !== undefined && <p className={styles.note}>{note}</p>}

      {pockets.length > 0 && (
        <ul
          className={styles.pockets}
          aria-label={`Pockets inside ${name} — already part of its balance`}
        >
          {pockets.map((pocket) => (
            <li key={pocket.id} className={styles.pocket}>
              <span>{pocket.name}</span>
              <MoneyText
                amount={pocket.balance}
                currency={currency}
                className={styles.pocketBalance}
              />
            </li>
          ))}
        </ul>
      )}

      {cards.length > 0 && <p className={styles.cards}>{cards.join(' \u00b7 ')}</p>}
    </Panel>
  );
}
