import { addDays, format as formatDate, fromParts, monthOf, yearOf } from '../../lib/dates';
import { subtract, toMinorUnits } from '../../lib/money';
import { billDateFor, nextDueDateOnOrAfter } from '../../lib/statementCycle';
import { MoneyText } from '../MoneyText';
import { Panel } from '../Panel';
import { ProgressBar } from '../ProgressBar';
import { TagChip } from '../TagChip';

import styles from './CardSummary.module.css';
import type { CardSummaryProps, CreditCardSummaryProps } from './CardSummary.types';

/**
 * One card: what it is, where it settles from, and — for a credit card — how
 * much room is left and when the bill lands.
 *
 * The cycle is explained with the card's own dates rather than in the abstract,
 * because BR-4 is the rule users most often get wrong: a purchase does not cost
 * money on the day it is spent. Saying "spend up to day 25 is billed on
 * 05-09-2026" answers that in a way "closing day 25" does not.
 */

/** Past this much of the limit the card reads as running out of room. */
const TIGHT_USAGE_PERCENT = 70;
const PERCENT = 100;

function CreditCardBody({
  name,
  creditLimit,
  currentBalance,
  closingDay,
  dueDay,
  today,
  currency = 'EUR',
  dateFormat = 'DD-MM-YYYY',
}: CreditCardSummaryProps) {
  const limit = toMinorUnits(creditLimit);
  const usagePercent = limit === 0 ? 0 : (toMinorUnits(currentBalance) / limit) * PERCENT;
  const isTight = usagePercent >= TIGHT_USAGE_PERCENT;

  const cycle = { closingDay, dueDay };
  // Two worked examples either side of the closing day. Stepping a day forward
  // rather than adding one to the day number keeps this correct at month end.
  const onClosingDay = fromParts(yearOf(today), monthOf(today), closingDay);
  const billedOnClosing = formatDate(billDateFor(onClosingDay, cycle), dateFormat);
  const billedAfterClosing = formatDate(billDateFor(addDays(onClosingDay, 1), cycle), dateFormat);

  return (
    <>
      <p className={styles.usage}>
        <span>
          <MoneyText amount={currentBalance} currency={currency} /> used of{' '}
          <MoneyText amount={creditLimit} currency={currency} />
        </span>
        <span className={isTight ? styles.availableTight : styles.available}>
          <MoneyText amount={subtract(creditLimit, currentBalance)} currency={currency} /> available
        </span>
      </p>

      <ProgressBar
        label={`${name} credit used`}
        value={usagePercent}
        tone={isTight ? 'negative' : 'accent'}
      />

      <div className={styles.cycle}>
        <div>
          <div className={styles.cycleLabel}>Closes</div>
          <div className={styles.cycleValue}>day {closingDay}</div>
        </div>
        <div>
          <div className={styles.cycleLabel}>Due</div>
          <div className={styles.cycleValue}>day {dueDay}</div>
        </div>
        <div>
          <div className={styles.cycleLabel}>Next bill</div>
          <div className={[styles.cycleValue, styles.nextBill].filter(Boolean).join(' ')}>
            {formatDate(nextDueDateOnOrAfter(today, dueDay), dateFormat)}
          </div>
        </div>
      </div>

      <p className={styles.note}>
        Spend up to day {closingDay} is billed on {billedOnClosing}. Anything after that waits for
        the next bill ({billedAfterClosing}).
      </p>
    </>
  );
}

export function CardSummary(props: CardSummaryProps) {
  const { name, settlesFrom, kind } = props;

  return (
    <Panel>
      <div className={styles.header}>
        <div className={styles.identity}>
          <h2 className={styles.name}>{name}</h2>
          <TagChip variant={kind === 'CREDIT' ? 'accent' : 'neutral'}>
            {kind === 'CREDIT' ? 'Credit' : 'Debit'}
          </TagChip>
        </div>
        <span className={styles.settlesFrom}>settles from {settlesFrom}</span>
      </div>

      {props.kind === 'CREDIT' ? (
        <CreditCardBody {...props} />
      ) : (
        <p className={styles.debitNote}>
          Spend leaves {settlesFrom} the same day — no statement cycle.
        </p>
      )}
    </Panel>
  );
}
