# ADR-3 — Ship mobile via Capacitor, not a native rewrite

**Status:** Accepted
**Date:** 2026-09-01

## Context

`SPEC-PROMPT.md` requires the app to be buildable so it "can later be wrapped as a mobile app for Android and iOS". ADR-2 makes the frontend a Vite web app, so a wrapping strategy has to be named now — otherwise "mobile later" is an unbacked promise.

The spec already does the architectural work that makes wrapping viable: business logic lives in the backend or in framework-agnostic TypeScript (§0 intro, §5), never in JSX. §5 also mandates mobile-first responsive layout with a 940px breakpoint, 44px minimum touch targets and keyboard-operable controls.

## Decision

Ship Android and iOS with **Capacitor**, wrapping the production Vite build. Deferred until the web app is complete (spec §6 step 11, Hardening).

## Consequences

- No source change is required to add it: `npx cap init`, `npx cap add ios android`, point `webDir` at `dist/`.
- The §0.6 component library has exactly one implementation, so a change to a component is a change to one folder — which is the whole point of §0.6.
- Native device APIs, if ever needed (biometric unlock, push for BR-12), come from Capacitor plugins behind a port interface in `lib/`, so the web build stays unaware.
- Cost: a WebView rather than native views. Acceptable — this is a forms-and-tables application, not a graphics-heavy one.
- Constraint this places on all future work: **the responsive rules and 44px touch targets in spec §5 are not optional polish.** They are the mobile build. Treat an accessibility or responsiveness miss as a broken feature.

## Alternative rejected

React Native / Expo from day one — see ADR-2. Also rejected: shipping web-only and deciding later. That defers a decision that constrains every component built between now and then.
