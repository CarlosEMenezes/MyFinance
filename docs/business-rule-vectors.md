# Business-rule vectors

Two implementations of BR-4, BR-6, BR-7, BR-9, BR-10 and BR-11 exist, and the
spec requires both: Java domain services own every figure that is persisted
(§4), and `frontend/src/lib/` computes the same rules for money the user has
not committed yet — the goal slider and the instalment preview (§5, ADR-7).

Two implementations of the same arithmetic will drift. **The only defence is
that both are tested against the numbers below.** A vector here is not an
example; it is the assertion. When a Java test and its TypeScript counterpart
disagree, one of them has drifted from this file, and this file is right.

Every amount is written the way a person writes it. Java stores it as
`BigDecimal` at scale 2, HALF_UP; TypeScript as an integer count of minor
units. `€71.50` is `new BigDecimal("71.50")` and `7150` respectively.

Sources: `frontend/src/lib/*.test.ts`, and the prototype's `DCLogic` for the
fixture data (see [design-reference.md](design-reference.md)).

---

## Money — rounding (ADR-6)

Both sides round **half away from zero**, which is `BigDecimal`'s `HALF_UP`.
TypeScript reads the digit string rather than going through `parseFloat`, so
`0.005` is never first mangled into binary floating point.

| Input | Minor units | Note |
|---|---|---|
| `"0.005"` | `1` | half away from zero, not half-to-even |
| `"-0.005"` | `-1` | and symmetrically for negatives — `Math.round` would give `0` |
| `"74.20"` | `7420` | |
| `"12."` | *rejected* | not an amount yet |
| `"twelve"` | *rejected* | |

---

## BR-4 — the credit-card statement cycle

`billDateFor(purchaseDate, closingDay, dueDay)`.

Two **independent** month rolls, and conflating them is the mistake this table
exists to catch:

1. `purchaseDay <= closingDay` → the statement closes this month, else next.
2. `dueDay <= closingDay` → the bill falls the month *after* the closing month.

Cycle days are validated to **1–28**. Later days do not exist in every month,
so no clamping rule is needed anywhere — which is why 29, 30 and 31 are
rejected rather than adjusted.

| Purchase | Closing | Due | Bill date | Why |
|---|---|---|---|---|
| 2026-08-20 | 25 | 5 | **2026-09-05** | before closing, joins this statement |
| 2026-08-26 | 25 | 5 | **2026-10-05** | after closing, waits for the next |
| 2026-08-05 | 10 | 28 | **2026-08-28** | due day after closing, so same month |
| 2026-08-12 | 10 | 28 | **2026-09-28** | after closing, still same-month due |
| 2026-08-25 | 25 | 5 | **2026-09-05** | on the closing day it still joins this statement |
| 2026-08-01 | 1 | 28 | **2026-08-28** | earliest closing day, purchase on it |
| 2026-08-02 | 1 | 28 | **2026-09-28** | earliest closing day, one day late |
| 2026-08-28 | 28 | 1 | **2026-09-01** | latest closing day, due before it → bill rolls |
| 2026-08-29 | 28 | 1 | **2026-10-01** | past latest closing → both rolls apply |
| 2026-08-31 | 28 | 5 | **2026-10-05** | a 31st purchase in a 31-day month |
| 2026-12-20 | 25 | 5 | **2027-01-05** | the due date crosses the year |
| 2026-12-26 | 25 | 5 | **2027-02-05** | both rolls cross the year |
| 2028-02-29 | 25 | 5 | **2028-04-05** | a leap day after closing |
| 2028-02-20 | 25 | 5 | **2028-03-05** | leap-year February before closing |
| 2027-02-28 | 28 | 28 | **2027-03-28** | due **equal to** closing always rolls a month |
| 2027-01-31 | 28 | 15 | **2027-03-15** | a 31st rolling into February still bills 15 March |

The bill date is **never** the purchase date, and always lands on `dueDay`.

`nextDueDateOnOrAfter(from, dueDay)`:

| From | Due day | Result |
|---|---|---|
| 2026-08-01 | 5 | 2026-08-05 |
| 2026-08-05 | 5 | 2026-08-05 — today counts |
| 2026-08-31 | 5 | 2026-09-05 |
| 2026-12-20 | 5 | 2027-01-05 |
| 2027-01-29 | 28 | 2027-02-28 |

---

## BR-6 — instalments and implied interest

`financedTotal = A × n`, `interest = financedTotal − P`.

**Interest free when `interest <= 0.01 × n`** — one cent per instalment. The
tolerance is checked *before* the solver runs, so rounding noise never produces
an APR.

Otherwise bisect `P = A(1 − (1+i)^−n) / i` over `(0, 3]`, then
`APR = (1+i)^periodsPerYear − 1` with 52 / 26 / 12. Display caps at `>900%`.

### The prototype's three plans

| Plan | P | n | A | Freq | Financed | Interest | Periodic rate | APR |
|---|---|---|---|---|---|---|---|---|
| Laptop repair — PC World | €399.00 | 6 | €71.50 | monthly | €429.00 | €30.00 | 0.02111472 | **28.5%** |
| Flight — Dublin–Porto | €184.00 | 3 | €61.34 | monthly | €184.02 | €0.02 | 0 | **0%** |
| Desk chair — Herman | €540.00 | 12 | €52.90 | monthly | €634.80 | €94.80 | 0.0258051 | **35.8%** |

The flight is the tolerance case: 3 × €61.34 = €184.02 against €184.00. Two
cents against a three-cent tolerance, so **interest free** — the solver is not
run at all.

Periodic rates are exact to 8 decimal places. The defining check: putting
`0.02111472` back into the annuity identity with `A = 71.50`, `n = 6`
reproduces `399` to 6 decimal places.

### The tolerance boundary

| P | n | A | Interest | Tolerance | Interest free? |
|---|---|---|---|---|---|
| €600.00 | 6 | €100.00 | €0.00 | €0.06 | yes — exact |
| €120.00 | 6 | €20.01 | €0.06 | €0.06 | yes — **exactly at** it |
| €120.00 | 6 | €20.02 | €0.12 | €0.06 | **no** — one cent past |
| €600.00 | 6 | €90.00 | −€60.00 | €0.06 | yes — a discount is not interest |

### Compounding and edges

| Case | P | n | A | Freq | Periodic | APR |
|---|---|---|---|---|---|---|
| Weekly compounds 52× | €500.00 | 10 | €55.00 | weekly | 0.01771543 | **149.2%** |
| Fortnightly compounds 26× | €500.00 | 10 | €55.00 | fortnightly | same periodic as monthly | > monthly APR |
| Single instalment | €100.00 | 1 | €110.00 | monthly | 0.1 | — |
| Above the display cap | €100.00 | 12 | €50.00 | monthly | — | **`>900%`** |

A single instalment of €110 for €100 now is simply 10% for the period — the
solver must return exactly that, not an approximation of it.

**Rejected terms:** `n <= 0`, fractional `n`, `P <= 0`, `A <= 0`.

---

## BR-7 — loans, mirrored

Same solver as BR-6. Adds the settlement figure — the remaining instalments
discounted back to today at the solved rate — and the early-payoff saving,
which is `remaining × A − settlementFigure` and **must be shown**.

### The prototype's two loans

| Loan | Principal | n | A | Freq | Paid |
|---|---|---|---|---|---|
| Credit union | €2,500.00 | 24 | €118.40 | monthly | 5 |
| Family — no interest | €600.00 | 6 | €100.00 | monthly | 3 |

Credit union:

| Figure | Value |
|---|---|
| Total repayable | €2,841.60 |
| Interest | €341.60 |
| Periodic rate | 0.01051039 |
| APR | **13.4%** |
| Instalments remaining | 19 |
| Remaining repayable | €2,249.60 |
| **Settlement figure today** | **€2,029.59** |
| **Early-payoff saving** | **€220.01** |

Family loan: interest €0.00, interest free, APR `0%`. With 3 of 6 paid,
remaining repayable €300.00, settlement €300.00, **saving €0.00** — settling an
interest-free loan early gains nothing, and the UI must not imply otherwise.

### Settlement edges

| Case | Settlement | Saving |
|---|---|---|
| Credit union, 0 paid | €2,500.00 | €341.60 — exactly the principal, exactly the interest |
| Credit union, 23 paid (1 left) | €117.17 | €1.23 — one period of discounting |
| Credit union, 24 paid (0 left) | €0.00 | €0.00 |

The saving is **never** larger than the interest.

**Rejected terms:** `instalmentsPaid < 0`, `> n`, fractional, `principal <= 0`.

---

## BR-9 — planned versus real

`variance = real − planned` for **both** category types. Only the tone differs.
The prototype flips the sign for expenses; the spec does not, and the spec wins
(see CLAUDE.md gotcha 18). One sign convention is also what lets a column of
variances be summed without asking what kind of row each one is.

| Type | Planned | Real | Variance | Tone |
|---|---|---|---|---|
| EARNING | €160.00 | €672.00 | +€512.00 | GOOD |
| EARNING | €1,200.00 | €1,010.00 | −€190.00 | BAD |
| EARNING | €450.00 | €450.00 | €0.00 | NEUTRAL |
| EARNING | €0.00 | €64.50 | +€64.50 | GOOD |
| EXPENSE | €60.00 | €318.40 | +€258.40 | BAD |
| EXPENSE | €45.00 | €44.98 | −€0.02 | GOOD |
| EXPENSE | €29.00 | €29.00 | €0.00 | NEUTRAL |

---

## BR-10 — period normalisation, counted on real dates

`occurrencesIn(frequency, anchorDate, range)` steps from the anchor by the
frequency and counts landings inside the range. **A month holding five paydays
plans five.** No averaging.

August 2026 is `2026-08-01 … 2026-08-31`.

| Frequency | Anchor | Range | Count | Why |
|---|---|---|---|---|
| MONTHLY | 2026-01-01 | August 2026 | 1 | |
| MONTHLY | 2026-01-01 | 2026 | 12 | |
| MONTHLY | 2026-01-31 | 2026 | 12 | a 31st anchor clamps into short months and still lands every month |
| MONTHLY | 2026-01-31 | September 2026 | 1 | clamped to the 30th |
| MONTHLY | 2026-09-15 | August 2026 | 0 | a new plan is never backdated |
| MONTHLY | 2026-08-12 | August 2026 | 1 | the anchor month counts |
| WEEKLY | 2026-01-05 | August 2026 | **5** | the five-payday month — *not* 52/12 |
| WEEKLY | 2026-01-05 | February 2026 | 4 | |
| WEEKLY | 2026-08-20 | August 2026 | 2 | counted from an anchor inside the range |
| WEEKLY | 2026-09-07 | August 2026 | 0 | |
| WEEKLY | 2026-01-05 | 2026-10-01 … 2026-10-31 | 4 | unaffected by the daylight-saving change |
| FORTNIGHTLY | 2026-01-05 | August 2026 | 3 | stepped from the anchor, not the weekly count halved |
| FORTNIGHTLY | 2026-01-12 | August 2026 | 2 | a different anchor alignment |

Single-day range `2026-08-31 … 2026-08-31`, weekly: anchor `2026-01-05` → 1,
anchor `2026-01-06` → 0. An inverted range is rejected.

`plannedAmountIn` is the per-occurrence amount times that count:
€160.00 weekly, anchored 2026-01-05, across August 2026 = **€800.00**.

### BR-3 — the averaged exception

`periodsPerMonth`: weekly **52/12**, fortnightly **26/12**, monthly **1**.

This is used **only** for the derived read-only rows ("Loan repayments", "Card
instalments"), never for a category plan. €160.00 weekly smooths to **€693.33**
per month — a figure that is correct as a commitment average and wrong as a
month's real cost. Keep the two apart; see CLAUDE.md gotcha 5.

`periodsPerYear` (BR-6's APR conversion only): 52 / 26 / 12.

---

## BR-11 — goals

`gap = target − saved`, floored at zero. Required contribution is
`gap / periodsUntilTarget`, where the horizon is **months multiplied out**, not
BR-10 calendar counting — a goal is a smooth target, not a schedule:

| Frequency | Periods in 4 months |
|---|---|
| MONTHLY | 4 |
| WEEKLY | 4 × 4.33 = 17.32 |
| DAILY | 4 × 30.4 = 121.6 |

### The prototype's three goals

| Goal | Target | Saved | Months | Gap | Per period | Progress |
|---|---|---|---|---|---|---|
| MacBook Air M4 | €1,349.00 | €410.00 | 4 | €939.00 | **€234.75**/mo | 30% |
| Emergency fund | €2,000.00 | €640.00 | 10 | €1,360.00 | **€136.00**/mo | 32% |
| Interrail summer | €900.00 | €80.00 | 9 | €820.00 | **€91.11**/mo | 9% |

The MacBook at other frequencies, same 4-month horizon: weekly **€54.21**,
daily **€7.72**. The **monthly requirement is €234.75 whatever the frequency** —
it is always stated, so two goals on different rhythms stay comparable.

Pushing the horizon out to 12 months drops the monthly figure to **€78.25**.

A reached goal has `gap = €0.00` and `contributionPerPeriod = €0.00`, never a
negative. Progress is clamped to 0–100: over-saved reads 100%, a zero target
reads 0% rather than dividing by zero.

**Feasibility** compares the per-month requirement to spare (planned in minus
planned out) and states the surplus or the shortfall — €234.75 against €400.00
spare is feasible; against €150.00 it is not; against exactly €234.75 it is
feasible with nothing to spare.

**Rejected terms:** `months <= 0`, `target <= 0`.

---

## What is not here yet

BR-1, BR-2, BR-12 and BR-15 are computed **only** on the server — the frontend
renders them and never recomputes (ADR-7), so there is no second
implementation to keep in step and no vector to share. Their figures are
asserted against the MSW fixtures in `frontend/src/test/`, which are themselves
taken from the prototype's `state` block.

BR-16 to BR-25 are Phase 2 and unimplemented.
