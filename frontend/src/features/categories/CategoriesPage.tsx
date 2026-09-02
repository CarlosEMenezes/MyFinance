import { EmptyState } from '../../components/EmptyState';
import { MoneyText } from '../../components/MoneyText';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { isNegative, subtract, sum } from '../../lib/money';

import styles from './CategoriesPage.module.css';
import { CategoryTable } from './components/CategoryTable';
import { useCategories, useUpdateCategoryPlan } from './hooks';

/**
 * What you expect to earn and spend (BR-14), and what that comes to (BR-10).
 *
 * The window note is not filler. BR-10 counts real dates, so a month holding
 * five paydays plans five — and a user who does not know which dates the
 * period covers cannot tell why a weekly plan of €160 came to €800.
 */

const EXPENSE_GROUPS = ['Fixed', 'Variable', 'Debt', 'Ungrouped'];
const EARNING_GROUPS = ['Employment', 'Self-employed', 'Occasional', 'Ungrouped'];

export function CategoriesPage() {
  const { expenses, earnings, periodLabel, periodFrom, periodTo, isLoading, error } =
    useCategories();
  const { mutate } = useUpdateCategoryPlan();

  const plannedIn = sum(earnings.map((row) => row.plannedInPeriod));
  const plannedOut = sum(expenses.map((row) => row.plannedInPeriod));
  const spare = subtract(plannedIn, plannedOut);

  return (
    <>
      <PageHeader kicker="Fig. 08 — What you expect to earn and spend" title="Categories & plan" />

      {isLoading && <p className={styles.status}>Loading categories…</p>}

      {error !== null && (
        <EmptyState title="Categories could not be loaded" message={error.message} />
      )}

      {!isLoading && error === null && expenses.length === 0 && earnings.length === 0 && (
        <EmptyState
          title="No categories yet"
          message="Create a category to set what you expect to earn or spend."
        />
      )}

      {(expenses.length > 0 || earnings.length > 0) && (
        <div className={styles.layout}>
          <div className={styles.tables}>
            <Panel title="Expense categories">
              <CategoryTable
                caption="Expense categories and their planned amounts"
                rows={expenses}
                groups={EXPENSE_GROUPS}
                onPlanChange={(id, amount) => {
                  mutate({ id, changes: { plannedAmount: amount } });
                }}
                onFrequencyChange={(id, plannedFrequency) => {
                  mutate({ id, changes: { plannedFrequency } });
                }}
                onGroupChange={(id, group) => {
                  mutate({ id, changes: { group } });
                }}
              />
            </Panel>

            <Panel title="Earning categories">
              <CategoryTable
                caption="Earning categories and their planned amounts"
                rows={earnings}
                groups={EARNING_GROUPS}
                onPlanChange={(id, amount) => {
                  mutate({ id, changes: { plannedAmount: amount } });
                }}
                onFrequencyChange={(id, plannedFrequency) => {
                  mutate({ id, changes: { plannedFrequency } });
                }}
                onGroupChange={(id, group) => {
                  mutate({ id, changes: { group } });
                }}
              />
            </Panel>
          </div>

          <Panel title="The plan for this period">
            <p className={styles.windowNote}>
              Counted across {periodLabel} ({periodFrom} → {periodTo}). Weekly and fortnightly plans
              are counted by real dates, so a period holding five paydays plans five — no averaging.
            </p>
            <ul className={styles.summary} aria-label="Plan summary">
              <li className={styles.summaryLine}>
                <span className={styles.summaryLabel}>Planned in</span>
                <MoneyText amount={plannedIn} className={styles.in} />
              </li>
              <li className={styles.summaryLine}>
                <span className={styles.summaryLabel}>Planned out</span>
                <MoneyText amount={plannedOut} className={styles.out} />
              </li>
              <li className={styles.summaryLine}>
                <span className={styles.summaryLabel}>Spare, {periodLabel}</span>
                <MoneyText amount={spare} className={isNegative(spare) ? styles.out : styles.in} />
              </li>
            </ul>
          </Panel>
        </div>
      )}
    </>
  );
}
