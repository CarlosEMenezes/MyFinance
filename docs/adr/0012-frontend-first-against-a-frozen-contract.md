# ADR-12 — The frontend was built first, against a frozen contract

**Status:** Accepted
**Date:** 2026-09-06

## Context

Spec §6 steps 1–11 describe **vertical slices**: each step cuts through domain,
persistence, API and UI together, so that a feature is finished in one pass and
nothing is built against a shape that later turns out to be wrong.

That is not how this project was built. The nine Phase 1 pages were written
first, in full, against `frontend/src/types/api.ts` and MSW; the backend is
following behind them, one feature at a time, satisfying that contract.

The deviation is real and deliberate, and until this ADR it was written down
nowhere. `docs/business-rule-vectors.md` — extracted before the Java
calculators were written, which was the right order — is a *consequence* of the
decision. The decision itself was missing.

## Decision

**Build the nine Phase 1 pages against a frozen API contract, then implement
the backend to satisfy that contract, rather than following §6's vertical
slices.**

`frontend/src/types/api.ts` is that contract. It was derived from spec §2 (the
domain model) and spec §4 (the API surface) — not from whatever was convenient
for a page — and it was written before any page existed.

## Why it is safe

The churn vertical slicing exists to prevent is **pages built against an
invented API shape**. Freezing the contract before any page existed prevents
exactly the same churn by a different route: a page cannot be written against a
shape the backend has not promised, because the shape is the thing it imports.

This was verified rather than assumed. At the time the decision was taken,
**zero pages existed** and **exactly one component leaked a business rule** —
`CardSummary`, which computed BR-4 bill dates through `lib/statementCycle`. It
was refactored to take the server-supplied dates as props before any page was
written. The remaining `lib/` modules (`period`, `instalments`, `loans`,
`goals`) had no component consumer at all, so there was nothing to unwind.

## What it costs

**Two implementations of BR-4, BR-6, BR-7, BR-9, BR-10 and BR-11 exist** — once
in Java, once in TypeScript. ADR-7 governs *where* a rule may live and settles
which side is authoritative for each figure. It does not stop the two drifting:
a rule can be authoritative on the server and still be quietly wrong in the
`lib/` copy that draws the live preview.

## The mitigation

Both sides are tested against the **identical numeric vectors** in
[docs/business-rule-vectors.md](../business-rule-vectors.md). That file is the
single source, and the rule is directional:

> **Neither implementation may be edited to agree with the other — only with
> the vectors.** When a Java test and its TypeScript counterpart disagree, one
> of them has drifted from that file, and the file is right.

This is not theoretical. It has already caught a real bug: BR-1's treatment of
loan principals, where the spec adds them to `availableNow` as their own term
and the code had folded them into the account balances. The spec was right and
the code was wrong; the code changed.

## MSW is temporary

`frontend/src/test/handlers.ts` is the contract standing in for a backend that
did not exist yet. It is **deleted when the real API lands, not extended.** A
handler answering something `types/api.ts` does not promise is a page inventing
an endpoint, and the frozen contract stops meaning anything the moment that is
allowed.

## Consequences

- Every backend feature slice is written to a contract that already exists and
  is already exercised by a page. If a DTO cannot match it, that is a finding
  to report, not a licence to change the frontend.
- The final step of Phase 1 is a swap, not an integration: delete
  `handlers.ts`, set `VITE_USE_MOCK_API=false`, and confirm every page renders
  the figures it rendered against fixtures. Any difference is a drift bug the
  vectors should have caught.
- Until that swap, `git diff --name-only` should show nothing under
  `frontend/src` while a backend slice is being built.

## Supersedes nothing

**ADR-7 stands unchanged.** It answers *where a rule may live*; this ADR
answers *in what order the two sides were built*. They are independent, and
ADR-7's boundary is what makes this order survivable.
