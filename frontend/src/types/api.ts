/**
 * The API contract, as it travels on the wire.
 *
 * Derived from spec §2 (domain model) and §4 (API surface), and frozen before
 * any page is written so that no screen can be built against a shape the
 * backend has not promised.
 *
 * Two conventions make the boundary cheap to cross:
 *
 * - Money is an **integer number of minor units**, matching `lib/money`. It
 *   arrives as a plain `number` and is converted with `fromMinorUnits`, which
 *   rejects anything fractional — so a bad payload fails loudly at the edge
 *   rather than becoming a wrong figure on screen.
 * - Dates are **ISO `YYYY-MM-DD`**, matching `lib/dates` and Java's
 *   `LocalDate`. Converted with `fromIso`, which rejects anything else.
 *
 * Per ADR-7, every figure a screen displays is a field here. If a page finds
 * itself calculating, the calculation belongs on the server and the result
 * belongs in this file.
 */

/** An exact amount in minor units (cents). Convert with `fromMinorUnits`. */
export type MinorUnits = number;

/** A calendar date, `YYYY-MM-DD`. Convert with `fromIso`. */
export type IsoDate = string;

/** An instant, ISO 8601 with offset. Used only for audit fields such as `readAt`. */
export type IsoInstant = string;

export type CurrencyCode = 'EUR' | 'USD' | 'GBP' | 'BRL';

export type PayCycle = 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY' | 'IRREGULAR';
export type PlannedFrequency = 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY';
export type ContributionFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type DateFormatPreference = 'DD-MM-YYYY' | 'MM-DD-YYYY' | 'YYYY-MM-DD';
export type WeekStart = 'MONDAY' | 'SUNDAY';

export type AccountKind = 'CASH' | 'BANK' | 'SAVINGS';
export type CardKind = 'CREDIT' | 'DEBIT';
export type CategoryType = 'EXPENSE' | 'EARNING';
export type TransactionType = 'EXPENSE' | 'EARNING' | 'SAVING';
export type JobRateType = 'FIXED_PER_JOB' | 'HOURLY' | 'PER_SESSION';
export type NotificationSource =
  'CARD_BILL' | 'LOAN' | 'INSTALMENT' | 'DIRECT_DEBIT' | 'SUBSCRIPTION';

/** BR-9. Computed server-side for persisted rows so no screen invents it. */
export type VarianceTone = 'GOOD' | 'BAD' | 'NEUTRAL';

/** The window a figure covers. `CUSTOM` uses the `from`/`to` query parameters. */
export type PeriodKind = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'CUSTOM';

/* ── identity and settings (spec §6 step 2) ─────────────────────────────── */

export interface UserPreferences {
  readonly autoConvertForeignAmounts: boolean;
  readonly roundGoalContributionsUp: boolean;
  readonly carryUnspentBudget: boolean;
}

export interface User {
  readonly id: string;
  readonly name: string;
  readonly age: number | null;
  readonly role: string | null;
  readonly country: string | null;
  readonly payCycle: PayCycle;
  readonly defaultCurrency: CurrencyCode;
  readonly dateFormat: DateFormatPreference;
  readonly weekStart: WeekStart;
  readonly preferences: UserPreferences;
}

/* ── accounts and pockets (BR-13) ───────────────────────────────────────── */

export interface Pocket {
  readonly id: string;
  readonly accountId: string;
  readonly name: string;
  /** Already part of the parent account's balance. Never added to it. */
  readonly balance: MinorUnits;
}

export interface Account {
  readonly id: string;
  readonly name: string;
  readonly kind: AccountKind;
  readonly balance: MinorUnits;
  readonly currency: CurrencyCode;
  readonly includeInTotals: boolean;
  readonly note: string | null;
  readonly pockets: readonly Pocket[];
  /** Names of the cards that settle from this account. */
  readonly cardNames: readonly string[];
}

/* ── cards (BR-4, BR-5) ─────────────────────────────────────────────────── */

/**
 * The three dates a credit card screen shows. Computed by
 * `StatementCycleCalculator`, never by the frontend (ADR-7).
 */
export interface CardCycleDates {
  /** The next time a bill actually falls due. */
  readonly nextBillDate: IsoDate;
  /** When spend on the closing day itself is billed. */
  readonly billDateOnClosingDay: IsoDate;
  /** When spend the day after closing is billed — the next statement. */
  readonly billDateAfterClosingDay: IsoDate;
}

export interface Card {
  readonly id: string;
  readonly name: string;
  readonly kind: CardKind;
  readonly accountId: string;
  /** The account name, so a card list needs no second request. */
  readonly settlesFrom: string;
  /** Credit cards only; `null` on a debit card, which has no cycle (BR-5). */
  readonly creditLimit: MinorUnits | null;
  readonly currentBalance: MinorUnits | null;
  readonly closingDay: number | null;
  readonly dueDay: number | null;
  readonly cycle: CardCycleDates | null;
}

/* ── categories and the plan (BR-10, BR-14) ─────────────────────────────── */

export interface Category {
  readonly id: string;
  readonly type: CategoryType;
  readonly name: string;
  readonly group: string;
  /** Per occurrence, in the frequency below. */
  readonly plannedAmount: MinorUnits;
  readonly plannedFrequency: PlannedFrequency;
  /** The date the recurrence is counted from (BR-10). */
  readonly anchorDate: IsoDate;
  readonly archived: boolean;
}

/**
 * `GET /categories?period=…`.
 *
 * The window comes with the list because BR-10 counts occurrences against real
 * dates, and where a period starts and ends is the server's to decide. The
 * client counts *within* that window while the user edits a frequency or an
 * anchor, which is the optimistic case ADR-7 allows.
 */
export interface CategoryList {
  readonly period: PeriodWindow;
  readonly categories: readonly Category[];
}

/** `PATCH /categories/{id}` — BR-14 inline plan editing. */
export interface UpdateCategoryPlanRequest {
  readonly plannedAmount?: MinorUnits;
  readonly plannedFrequency?: PlannedFrequency;
  readonly anchorDate?: IsoDate;
  readonly group?: string;
}

/* ── transactions (BR-8) ────────────────────────────────────────────────── */

export interface Transaction {
  readonly id: string;
  readonly type: TransactionType;
  readonly categoryId: string;
  /** In the currency it was logged in. */
  readonly amount: MinorUnits;
  readonly currency: CurrencyCode;
  readonly amountInDefaultCurrency: MinorUnits;
  /** The rate used at log time. Display only — never used to recompute. */
  readonly fxRate: number;
  readonly date: IsoDate;
  /** An account id or a card id. */
  readonly paymentMethodId: string;
  readonly note: string | null;
  readonly instalmentPlanId: string | null;
  readonly loanId: string | null;
  /**
   * BR-4. For a credit-card expense this is the bill date the amount lands on,
   * which is not `date`. `null` for anything settled immediately.
   */
  readonly plannedExpenseDate: IsoDate | null;
}

/* ── financing (BR-6, BR-7) ─────────────────────────────────────────────── */

/** The interest figures, computed by `InstalmentCalculator` / `LoanCalculator`. */
export interface InterestSummary {
  readonly financedTotal: MinorUnits;
  readonly interest: MinorUnits;
  readonly interestFree: boolean;
  /** Rate per instalment period, as a fraction. Zero when interest free. */
  readonly periodicRate: number;
  /** As a fraction. Zero when interest free. */
  readonly annualRate: number;
  /** True when the APR exceeds the 900% the UI will print (BR-6). */
  readonly aboveDisplayCap: boolean;
}

export interface InstalmentPlan {
  readonly id: string;
  readonly cardId: string;
  readonly label: string;
  readonly cashPrice: MinorUnits;
  readonly instalmentCount: number;
  readonly instalmentAmount: MinorUnits;
  readonly frequency: PlannedFrequency;
  readonly instalmentsPaid: number;
  readonly firstDueDate: IsoDate;
  readonly interest: InterestSummary;
}

export interface Loan {
  readonly id: string;
  readonly label: string;
  readonly principal: MinorUnits;
  readonly instalmentCount: number;
  readonly instalmentAmount: MinorUnits;
  readonly frequency: PlannedFrequency;
  readonly instalmentsPaid: number;
  readonly firstDueDate: IsoDate;
  readonly depositAccountId: string;
  readonly interest: InterestSummary;
  /** BR-7, computed server-side. */
  readonly instalmentsRemaining: number;
  readonly remainingRepayable: MinorUnits;
  readonly settlementFigureToday: MinorUnits;
  readonly earlyPayoffSaving: MinorUnits;
}

/* ── jobs and goals (BR-11) ─────────────────────────────────────────────── */

export interface Job {
  readonly id: string;
  readonly categoryId: string;
  readonly name: string;
  readonly rateType: JobRateType;
  readonly rate: MinorUnits;
  readonly unitsLogged: number;
  readonly status: string;
  readonly total: MinorUnits;
}

export interface Goal {
  readonly id: string;
  readonly name: string;
  readonly targetAmount: MinorUnits;
  readonly targetDate: IsoDate;
  readonly savedAmount: MinorUnits;
  readonly contributionFrequency: ContributionFrequency;
  readonly pocketId: string | null;
  readonly rank: number;
  /** BR-11, computed server-side. */
  readonly gap: MinorUnits;
  readonly contributionPerPeriod: MinorUnits;
  readonly monthlyRequirement: MinorUnits;
  readonly progressPercent: number;
  /** Where the plan says progress should have reached by now. */
  readonly pacePercent: number;
  readonly onPace: boolean;
}

/* ── notifications (BR-12) ──────────────────────────────────────────────── */

export interface Notification {
  readonly key: string;
  readonly label: string;
  readonly detail: string;
  readonly dueDate: IsoDate;
  readonly daysUntilDue: number;
  readonly amount: MinorUnits;
  readonly sourceType: NotificationSource;
  readonly readAt: IsoInstant | null;
}

export interface NotificationSettings {
  /** A subset of {10, 5, 2}. */
  readonly leadDays: readonly number[];
  readonly channels: {
    readonly push: boolean;
    readonly email: boolean;
    readonly weeklySummary: boolean;
  };
}

/* ── the dashboard payload (BR-1, BR-2, BR-3, BR-9, BR-10, BR-15) ───────── */

/**
 * One category row, already normalised to the requested period and with its
 * variance resolved. Spec §4: the frontend must not recompute these.
 */
export interface PlanRow {
  readonly categoryId: string;
  readonly category: string;
  readonly type: CategoryType;
  readonly group: string;
  /** For the whole period (BR-10 real-date counting). */
  readonly planned: MinorUnits;
  readonly real: MinorUnits;
  /** `real − planned`, same sign convention on both sides (BR-9). */
  readonly variance: MinorUnits;
  readonly varianceTone: VarianceTone;
  /** Per occurrence, the amount the inline field edits (BR-14). */
  readonly perOccurrence: MinorUnits;
  readonly frequency: PlannedFrequency;
  readonly occurrencesInPeriod: number;
  /** The averaged commitment figure, stated beside the real one (BR-3). */
  readonly monthlyEquivalent: MinorUnits;
  /**
   * BR-14. A derived row — "Loan repayments", "Card instalments" — is read
   * only and must be rendered as text, never as an input.
   */
  readonly derived: boolean;
  readonly paidWith: string | null;
  readonly dueNote: string | null;
  /** Set when the amount was logged in another currency (BR-8). */
  readonly foreignAmount: string | null;
}

export interface PeriodWindow {
  readonly kind: PeriodKind;
  readonly from: IsoDate;
  readonly to: IsoDate;
  /** How the window reads in prose, e.g. "August 2026". */
  readonly label: string;
}

/** BR-1 and BR-2. Every figure here is computed server-side. */
export interface Position {
  readonly totalMoneyNow: MinorUnits;
  readonly availableNow: MinorUnits;
  readonly owed: MinorUnits;
  readonly owedOnCards: MinorUnits;
  readonly owedOnInstalments: MinorUnits;
  readonly owedOnLoans: MinorUnits;
  /** Included in `availableNow`; the net effect on the total is the interest. */
  readonly borrowed: MinorUnits;
}

export interface UpcomingPayment {
  readonly key: string;
  readonly label: string;
  readonly detail: string;
  readonly date: IsoDate;
  /** Negative for money leaving, positive for money arriving. */
  readonly amount: MinorUnits;
}

export interface CategorySpend {
  readonly categoryId: string;
  readonly label: string;
  readonly real: MinorUnits;
  readonly planned: MinorUnits;
  /** Already scaled against the largest row, so the bar needs no arithmetic. */
  readonly percentOfLargest: number;
  readonly plannedPercentOfLargest: number;
}

export interface Dashboard {
  readonly period: PeriodWindow;
  readonly position: Position;
  readonly earnings: readonly PlanRow[];
  readonly expenses: readonly PlanRow[];
  readonly totals: {
    readonly earningsPlanned: MinorUnits;
    readonly earningsReal: MinorUnits;
    readonly expensesPlanned: MinorUnits;
    readonly expensesReal: MinorUnits;
    readonly netPlanned: MinorUnits;
    readonly netReal: MinorUnits;
  };
  readonly upcoming: readonly UpcomingPayment[];
  readonly categorySpend: readonly CategorySpend[];
  readonly accounts: readonly Account[];
}

/* ── foreign exchange (BR-8) ────────────────────────────────────────────── */

export interface FxRates {
  readonly base: CurrencyCode;
  readonly rates: Readonly<Record<CurrencyCode, number>>;
  /** Shown in the UI so the user knows how fresh the conversion is. */
  readonly fetchedAt: IsoInstant;
}

/* ── previews: figures for money not yet committed (spec §4) ────────────── */

export interface InstalmentPreviewRequest {
  readonly cashPrice: MinorUnits;
  readonly instalmentCount: number;
  readonly instalmentAmount: MinorUnits;
  readonly frequency: PlannedFrequency;
  readonly cardId: string;
  readonly purchaseDate: IsoDate;
}

export interface InstalmentPreview {
  readonly interest: InterestSummary;
  /** BR-4: when the first instalment actually lands. */
  readonly firstDueDate: IsoDate;
}

export interface LoanPreviewRequest {
  readonly principal: MinorUnits;
  readonly instalmentCount: number;
  readonly instalmentAmount: MinorUnits;
  readonly frequency: PlannedFrequency;
}

export interface LoanPreview {
  readonly interest: InterestSummary;
  /** BR-2: what borrowing adds to each side of the position. */
  readonly addsToAvailable: MinorUnits;
  readonly addsToOwed: MinorUnits;
  readonly settlementFigureToday: MinorUnits;
}

/* ── errors (RFC 7807, spec §4) ─────────────────────────────────────────── */

export interface FieldError {
  readonly field: string;
  readonly message: string;
}

export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail: string;
  /** Present on a 400. */
  readonly errors?: readonly FieldError[];
}
