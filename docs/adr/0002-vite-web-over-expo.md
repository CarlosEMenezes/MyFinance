# ADR-2 — Replace the Expo/React-Native frontend with Vite + React web

**Status:** Accepted
**Date:** 2026-09-01

## Context

`FrontEnd/BudgetTracker/` held the stock Expo Router starter template — "Tab One" / "Tab Two", `EditScreenInfo`, `Themed.tsx`, `Colors.ts` with `#2f95dc`. No application code, no `node_modules`.

`SPEC-PROMPT.md` §5 specifies React + TypeScript + **Vite**, React Router and TanStack Query. The spec's intro also says the app must be buildable so it "can later be wrapped as a mobile app for Android and iOS".

The design prototype is a **web** artefact. Its entire visual system depends on browser CSS that React Native does not have:

- CSS custom properties for every token (`--color-*`, `--space-*`, `--radius-*`, `--shadow-*`)
- `color-mix(in srgb, …)` — used for nearly every muted text colour and divider
- `:has(input:checked)` — how the segmented control renders its selected state
- `::before` / `::after` — how the `.blueprint` corner registration marks are drawn
- `@media (max-width: 940px)` — swaps the sidebar for the bottom tab bar
- `font-variant-numeric: tabular-nums` — required by spec §5 for every money figure

Reproducing this in RN `StyleSheet` would mean re-deriving the whole design system by hand, and it still would not be pixel-accurate.

## Decision

Delete the Expo template. Scaffold `frontend/` as Vite + React 18 + TypeScript strict, per spec §5. Port the Industry `styles.css` token sheet directly.

## Consequences

- The design can be reproduced exactly, because it is expressed in the same medium it was authored in.
- The prototype's CSS is a direct asset rather than something to be translated.
- Mobile is not lost — it is deferred to ADR-3 (Capacitor), which wraps the built web app and needs no source change.
- Nothing of value is discarded: the template contained zero application code.

## Alternative rejected

Keep Expo and re-author the design system in RN. Rejected: substantially more work, a design that would not match, and it would fork the §0.6 component library into two implementations.
