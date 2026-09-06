import { useState } from 'react';

import { Dialog } from '../../components/Dialog';
import { Field } from '../../components/Field';
import { SegmentedControl } from '../../components/SegmentedControl';
import { today } from '../../lib/dates';
import { toMinorUnits, tryFromDecimal } from '../../lib/money';
import type { CategoryType, CreateCategoryRequest, PlannedFrequency } from '../../types/api';

import styles from './NewCategoryDialog.module.css';

/**
 * Creating a category (BR-14).
 *
 * The planned amount and its frequency are part of creating the category, not
 * a second step: BR-14 says creating a category creates its planned amount,
 * and a category without one is a row the plan-vs-real tables cannot draw.
 *
 * The anchor date is what BR-10 counts occurrences from, so it is asked for
 * rather than assumed — a weekly plan anchored to a Monday and one anchored to
 * a Friday land a different number of times in the same month.
 */

const TYPES: readonly { readonly value: CategoryType; readonly label: string }[] = [
  { value: 'EXPENSE', label: 'Expense' },
  { value: 'EARNING', label: 'Earning' },
];

const FREQUENCIES: readonly { readonly value: PlannedFrequency; readonly label: string }[] = [
  { value: 'WEEKLY', label: 'week' },
  { value: 'FORTNIGHTLY', label: 'fortnight' },
  { value: 'MONTHLY', label: 'month' },
];

export interface NewCategoryDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly onCreate: (category: CreateCategoryRequest) => void;
  readonly expenseGroups: readonly string[];
  readonly earningGroups: readonly string[];
  readonly error?: string | undefined;
}

export function NewCategoryDialog({
  open,
  onClose,
  onCreate,
  expenseGroups,
  earningGroups,
  error,
}: NewCategoryDialogProps) {
  const [name, setName] = useState('');
  const [type, setType] = useState<CategoryType>('EXPENSE');
  const [groupOverride, setGroupOverride] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [frequency, setFrequency] = useState<PlannedFrequency>('MONTHLY');
  const [anchorDate, setAnchorDate] = useState<string>(today());
  const [attempted, setAttempted] = useState(false);

  // Groups belong to a side: "Fixed" is not an earnings group. Switching the
  // type therefore has to reset the choice rather than carry it across.
  const groups = type === 'EXPENSE' ? expenseGroups : earningGroups;
  const group =
    groupOverride !== null && groups.includes(groupOverride) ? groupOverride : (groups[0] ?? '');

  const parsedAmount = tryFromDecimal(amount);
  const nameError = attempted && name.trim() === '' ? 'Give the category a name.' : undefined;
  const amountError =
    attempted && parsedAmount === null ? 'Enter a planned amount, such as 60.00.' : undefined;

  const submit = () => {
    setAttempted(true);
    if (name.trim() === '' || parsedAmount === null) {
      return;
    }
    onCreate({
      name: name.trim(),
      type,
      group,
      plannedAmount: toMinorUnits(parsedAmount),
      plannedFrequency: frequency,
      anchorDate,
    });
  };

  return (
    <Dialog
      open={open}
      title="New category"
      onClose={onClose}
      actions={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn-primary" onClick={submit}>
            Create category
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
            placeholder="e.g. Course fees"
            aria-describedby={describedBy}
            onChange={(event) => {
              setName(event.target.value);
            }}
          />
        )}
      </Field>

      <SegmentedControl
        name="category-type"
        label="Kind"
        options={TYPES}
        value={type}
        onChange={setType}
      />

      <Field label="Group">
        {({ id }) => (
          <select
            id={id}
            className="input"
            value={group}
            onChange={(event) => {
              setGroupOverride(event.target.value);
            }}
          >
            {groups.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        )}
      </Field>

      <div className={styles.pair}>
        <Field label="Planned amount" error={amountError}>
          {({ id, describedBy }) => (
            <input
              id={id}
              className="input"
              type="text"
              inputMode="decimal"
              value={amount}
              placeholder="0.00"
              aria-describedby={describedBy}
              onChange={(event) => {
                setAmount(event.target.value);
              }}
            />
          )}
        </Field>

        <Field label="Every">
          {({ id }) => (
            <select
              id={id}
              className="input"
              value={frequency}
              onChange={(event) => {
                setFrequency(event.target.value as PlannedFrequency);
              }}
            >
              {FREQUENCIES.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          )}
        </Field>
      </div>

      <Field
        label="Counted from"
        hint="Weekly and fortnightly plans are counted by real dates, so where they start decides how many times they land in a period."
      >
        {({ id, describedBy }) => (
          <input
            id={id}
            className="input"
            type="date"
            value={anchorDate}
            aria-describedby={describedBy}
            onChange={(event) => {
              setAnchorDate(event.target.value);
            }}
          />
        )}
      </Field>
    </Dialog>
  );
}
