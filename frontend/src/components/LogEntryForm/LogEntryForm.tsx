import { useState } from 'react';

import { format as formatDate, type CalendarDate } from '../../lib/dates';
import { format as formatMoney, fromDecimal, multiply, type Currency } from '../../lib/money';
import type { Frequency } from '../../lib/period';
import { billDateFor } from '../../lib/statementCycle';
import { Checkbox } from '../Checkbox';
import { Dialog } from '../Dialog';
import { InstalmentCalculatorPanel } from '../InstalmentCalculatorPanel';
import { SegmentedControl } from '../SegmentedControl';

import styles from './LogEntryForm.module.css';
import type { EntryType, LogEntryFormProps, PaymentMethodOption } from './LogEntryForm.types';

/**
 * Logging money in or out.
 *
 * Two rules are load-bearing here, and both are about telling the truth before
 * the user commits:
 *
 * - BR-8. An amount in another currency is stored in that currency, with the
 *   rate used at log time. If no rate can be had the save is blocked with a
 *   clear error, because guessing a rate would silently corrupt every total
 *   that entry ever appears in.
 * - BR-4. A card purchase does not cost money on the day it is spent, so the
 *   form states the date it will actually be billed before saving. That date
 *   is computed here from lib/statementCycle, which ADR-7 permits for an entry
 *   that has not been saved; the server recomputes it on save.
 */

const ENTRY_TYPES = [
  { value: 'EXPENSE', label: 'Expense' },
  { value: 'EARNING', label: 'Earning' },
  { value: 'SAVING', label: 'Saving' },
] as const satisfies readonly { value: EntryType; label: string }[];

const CURRENCIES: readonly Currency[] = ['EUR', 'USD', 'GBP', 'BRL'];

const PAYMENT_LABEL: Record<EntryType, string> = {
  EXPENSE: 'Paid with',
  EARNING: 'Paid into',
  SAVING: 'Save into',
};

const RATE_DECIMALS = 3;

const isCreditCard = (method: PaymentMethodOption | undefined): boolean =>
  method?.kind === 'CREDIT_CARD';

export function LogEntryForm({
  open,
  onClose,
  onSubmit,
  categories,
  paymentMethods,
  defaultCurrency,
  fx,
  today,
  dateFormat = 'DD-MM-YYYY',
}: LogEntryFormProps) {
  const [type, setType] = useState<EntryType>('EXPENSE');
  const [amountText, setAmountText] = useState('');
  const [currency, setCurrency] = useState<Currency>(defaultCurrency);
  const [categoryId, setCategoryId] = useState('');
  const [paymentMethodId, setPaymentMethodId] = useState('');
  const [financing, setFinancing] = useState(false);
  const [instalmentCount, setInstalmentCount] = useState('6');
  const [instalmentAmount, setInstalmentAmount] = useState('');
  const [frequency, setFrequency] = useState<Frequency>('MONTHLY');
  const [error, setError] = useState<string | null>(null);

  const available = categories.filter((category) => category.type === type);
  const selectedCategoryId = categoryId === '' ? (available[0]?.id ?? '') : categoryId;
  const selectedMethodId = paymentMethodId === '' ? (paymentMethods[0]?.id ?? '') : paymentMethodId;
  const method = paymentMethods.find((candidate) => candidate.id === selectedMethodId);

  const rate = fx.status === 'READY' ? fx.rates[currency] : undefined;
  const needsConversion = currency !== defaultCurrency;

  let amount = null;
  try {
    const parsed = fromDecimal(amountText);
    amount = parsed > 0 ? parsed : null;
  } catch {
    amount = null;
  }

  const billDate: CalendarDate | undefined =
    isCreditCard(method) && method?.closingDay !== undefined && method.dueDay !== undefined
      ? billDateFor(today, { closingDay: method.closingDay, dueDay: method.dueDay })
      : undefined;

  const financeToggleShown = type === 'EARNING' || (type === 'EXPENSE' && isCreditCard(method));

  const handleSubmit = () => {
    if (amount === null) {
      setError('Enter an amount before saving.');
      return;
    }
    if (needsConversion && rate === undefined) {
      setError(
        `No exchange rate is available for ${currency}. The entry cannot be saved until a rate can be fetched.`,
      );
      return;
    }

    let plan = null;
    if (financing) {
      const count = Number(instalmentCount);
      try {
        const perInstalment = fromDecimal(instalmentAmount);
        if (Number.isInteger(count) && count > 0 && perInstalment > 0) {
          plan = { instalmentCount: count, instalmentAmount: perInstalment, frequency };
        }
      } catch {
        plan = null;
      }
    }

    setError(null);
    onSubmit({
      type,
      amount,
      currency,
      categoryId: selectedCategoryId,
      paymentMethodId: selectedMethodId,
      date: today,
      financing: plan,
    });
  };

  return (
    <Dialog
      open={open}
      title="Log entry"
      onClose={onClose}
      actions={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn-primary" onClick={handleSubmit}>
            Save entry
          </button>
        </>
      }
    >
      <SegmentedControl
        name="entry-type"
        label="Kind of entry"
        hideLabel
        options={ENTRY_TYPES}
        value={type}
        onChange={(next) => {
          setType(next);
          setCategoryId('');
          setFinancing(false);
        }}
      />

      <div className={styles.pair}>
        <label className="field">
          Amount
          <input
            className="input"
            type="text"
            inputMode="decimal"
            value={amountText}
            onChange={(event) => {
              setAmountText(event.target.value);
            }}
          />
        </label>
        <label className="field">
          Currency
          <select
            className="input"
            value={currency}
            onChange={(event) => {
              setCurrency(event.target.value as Currency);
            }}
          >
            {CURRENCIES.map((code) => (
              <option key={code} value={code}>
                {code}
              </option>
            ))}
          </select>
        </label>
      </div>

      {needsConversion && amount !== null && rate !== undefined && fx.status === 'READY' && (
        <p className={styles.conversion}>
          Stored as {currency}. Totals will show{' '}
          {formatMoney(multiply(amount, rate), defaultCurrency)} at today&apos;s rate (1 {currency}{' '}
          = {rate.toFixed(RATE_DECIMALS)} {defaultCurrency}), pulled {fx.fetchedAt}.
        </p>
      )}

      <div className={styles.even}>
        <label className="field">
          Category
          <select
            className="input"
            value={selectedCategoryId}
            onChange={(event) => {
              setCategoryId(event.target.value);
            }}
          >
            {available.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          {PAYMENT_LABEL[type]}
          <select
            className="input"
            value={selectedMethodId}
            onChange={(event) => {
              setPaymentMethodId(event.target.value);
            }}
          >
            {paymentMethods.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {billDate !== undefined && (
        <p className={styles.cardHint}>
          Card purchase — it becomes a planned expense on {formatDate(billDate, dateFormat)}, not on{' '}
          {formatDate(today, dateFormat)}.
        </p>
      )}

      {financeToggleShown && (
        <div className={styles.financeToggle}>
          <Checkbox
            label={
              type === 'EARNING'
                ? 'This is a loan — repaid in instalments'
                : 'Pay in instalments on this card'
            }
            checked={financing}
            onChange={setFinancing}
          />
        </div>
      )}

      {financing && financeToggleShown && (
        <InstalmentCalculatorPanel
          mode={type === 'EARNING' ? 'LOAN' : 'INSTALMENT'}
          principal={amount ?? fromDecimal('0')}
          instalmentCount={instalmentCount}
          instalmentAmount={instalmentAmount}
          frequency={frequency}
          onInstalmentCountChange={setInstalmentCount}
          onInstalmentAmountChange={setInstalmentAmount}
          onFrequencyChange={setFrequency}
          firstDueDate={billDate}
          currency={currency}
          dateFormat={dateFormat}
        />
      )}

      {error !== null && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </Dialog>
  );
}
