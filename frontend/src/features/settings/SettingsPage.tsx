import { Checkbox } from '../../components/Checkbox';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { SegmentedControl } from '../../components/SegmentedControl';
import { format as formatDate, fromIso } from '../../lib/dates';
import type {
  CurrencyCode,
  DateFormatPreference,
  PayCycle,
  User,
  UserPreferences,
  WeekStart,
} from '../../types/api';

import styles from './SettingsPage.module.css';
import { TextField } from './TextField';
import { useSettings } from './hooks';

/**
 * Who you are and how figures are shown.
 *
 * Nothing here is computed. The one rule the page carries is BR-8: the default
 * currency is what every total is stated in, and the FX rates behind those
 * conversions are shown with the time they were pulled — a converted figure
 * whose provenance is invisible is a figure taken on trust.
 *
 * Every field saves on its own. The form has no submit button because there is
 * no moment where a half-filled profile is worth rejecting: each field is
 * independently valid or it is not saved.
 */

const PAY_CYCLES: readonly { readonly value: PayCycle; readonly label: string }[] = [
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'FORTNIGHTLY', label: 'Fortnightly' },
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'IRREGULAR', label: 'Irregular' },
];

const CURRENCIES: readonly { readonly value: CurrencyCode; readonly label: string }[] = [
  { value: 'EUR', label: 'EUR € — Euro' },
  { value: 'USD', label: 'USD $ — US Dollar' },
  { value: 'GBP', label: 'GBP £ — Pound Sterling' },
  { value: 'BRL', label: 'BRL R$ — Brazilian Real' },
];

const DATE_FORMATS: readonly { readonly value: DateFormatPreference; readonly label: string }[] = [
  { value: 'DD-MM-YYYY', label: 'DD-MM-YYYY' },
  { value: 'MM-DD-YYYY', label: 'MM-DD-YYYY' },
  { value: 'YYYY-MM-DD', label: 'YYYY-MM-DD' },
];

/** The currency hint is one element on one page, so it needs no generated id. */
const CURRENCY_HINT_ID = 'default-currency-hint';

const WEEK_STARTS: readonly { readonly value: WeekStart; readonly label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'SUNDAY', label: 'Sunday' },
];

const PREFERENCES: readonly {
  readonly key: keyof UserPreferences;
  readonly label: string;
  readonly hint: string;
}[] = [
  {
    key: 'autoConvertForeignAmounts',
    label: 'Convert foreign amounts automatically',
    hint: 'Totals are stated in your default currency; the entry keeps the currency it was logged in',
  },
  {
    key: 'roundGoalContributionsUp',
    label: 'Round goal contributions up',
    hint: 'To the nearest whole unit, so a target is never left a cent short',
  },
  {
    key: 'carryUnspentBudget',
    label: 'Carry unspent budget into the next period',
    hint: 'What is left in a category rolls forward instead of resetting',
  },
];

/**
 * An age the user typed. Empty clears it; anything that is not a plain number
 * is not an age, and is refused rather than saved as something else.
 */
function parseAge(text: string): number | null | undefined {
  const trimmed = text.trim();
  if (trimmed === '') {
    return null;
  }
  return /^\d{1,3}$/.test(trimmed) ? Number(trimmed) : undefined;
}

/**
 * When the rates were pulled, in the user's own date format.
 *
 * Rendered in the offset the provider sent, not the reader's: the point of the
 * stamp is when the provider last quoted, and shifting it to another zone
 * would quietly restate the provider's claim.
 */
function describeFetchedAt(instant: string, dateFormat: DateFormatPreference): string {
  return `${formatDate(fromIso(instant.slice(0, 10)), dateFormat)} ${instant.slice(11, 16)}`;
}

/** Restates the saved profile as a sentence, so the fields read as a person. */
function describeProfile(user: User): string {
  const parts = [user.name === '' ? 'You' : user.name];
  if (user.age !== null) {
    parts.push(String(user.age));
  }
  if (user.role !== null && user.role !== '') {
    parts.push(user.role);
  }
  const cycle = PAY_CYCLES.find((option) => option.value === user.payCycle)?.label ?? '';
  return `${parts.join(', ')} — ${cycle.toLowerCase()} pay cycle. New earning categories are planned against it.`;
}

export function SettingsPage() {
  const { user, save, fxRates, fxFetchedAt, isLoading, error } = useSettings();

  return (
    <>
      <PageHeader kicker="Fig. 08 — Who you are and how figures are shown" title="Settings" />

      {isLoading && <p className={styles.status}>Loading your settings…</p>}

      {error !== null && (
        <EmptyState title="Settings could not be loaded" message={error.message} />
      )}

      {user !== undefined && (
        <div className={styles.layout}>
          <Panel title="Profile">
            <TextField
              label="Full name"
              value={user.name}
              placeholder="Your name"
              onCommit={(name) => {
                save({ name });
              }}
            />

            <div className={styles.pair}>
              <TextField
                label="Age"
                value={user.age === null ? '' : String(user.age)}
                placeholder="24"
                inputMode="numeric"
                onCommit={(text) => {
                  const age = parseAge(text);
                  // `undefined` means it was not an age. Leaving it unsaved
                  // makes the field snap back to what is stored, which is the
                  // truthful answer to "what did that save as?".
                  if (age !== undefined) {
                    save({ age });
                  }
                }}
              />
              <TextField
                label="Role"
                value={user.role ?? ''}
                placeholder="e.g. Freelance designer"
                onCommit={(role) => {
                  save({ role: role === '' ? null : role });
                }}
              />
            </div>

            <div className={styles.pair}>
              <TextField
                label="Country"
                value={user.country ?? ''}
                placeholder="Ireland"
                onCommit={(country) => {
                  save({ country: country === '' ? null : country });
                }}
              />

              <label className={styles.field}>
                <span className={styles.label}>Pay cycle</span>
                <select
                  className="input"
                  value={user.payCycle}
                  onChange={(event) => {
                    save({ payCycle: event.target.value as PayCycle });
                  }}
                >
                  {PAY_CYCLES.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <p className={styles.note}>{describeProfile(user)}</p>
          </Panel>

          <Panel title="Money and dates">
            <div className={styles.field}>
              <label>
                <span className={styles.label}>Default currency</span>
                <select
                  className="input"
                  value={user.defaultCurrency}
                  aria-describedby={CURRENCY_HINT_ID}
                  onChange={(event) => {
                    save({ defaultCurrency: event.target.value as CurrencyCode });
                  }}
                >
                  {CURRENCIES.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <span className={styles.hint} id={CURRENCY_HINT_ID}>
                Every total is stated in this. Entries keep the currency they were logged in.
              </span>
            </div>

            <SegmentedControl
              name="date-format"
              label="Date format"
              options={DATE_FORMATS}
              value={user.dateFormat}
              onChange={(dateFormat) => {
                save({ dateFormat });
              }}
              className={styles.segment}
            />

            <SegmentedControl
              name="week-start"
              label="Week starts on"
              options={WEEK_STARTS}
              value={user.weekStart}
              onChange={(weekStart) => {
                save({ weekStart });
              }}
              className={styles.segment}
            />

            <div className={styles.toggles}>
              {PREFERENCES.map((preference) => (
                <Checkbox
                  key={preference.key}
                  label={preference.label}
                  hint={preference.hint}
                  checked={user.preferences[preference.key]}
                  onChange={(on) => {
                    save({ preferences: { ...user.preferences, [preference.key]: on } });
                  }}
                />
              ))}
            </div>

            <div className={styles.fx}>
              <h3 className={styles.fxTitle}>Exchange rates</h3>
              {fxFetchedAt === undefined ? (
                // BR-8: never imply a conversion is current when no rate has
                // been fetched. Saying nothing would let stale figures pass.
                <p className={styles.hint}>No rates fetched yet.</p>
              ) : (
                <>
                  <ul className={styles.rates}>
                    {fxRates.map((rate) => (
                      <li key={rate.currency}>
                        <span className="tabular">
                          1 {user.defaultCurrency} = {rate.rate.toFixed(4)} {rate.currency}
                        </span>
                      </li>
                    ))}
                  </ul>
                  <p className={styles.hint}>
                    Last pulled {describeFetchedAt(fxFetchedAt, user.dateFormat)}
                  </p>
                </>
              )}
            </div>
          </Panel>
        </div>
      )}
    </>
  );
}
