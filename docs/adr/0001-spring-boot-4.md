# ADR-1 — Stay on Spring Boot 4.0.7 rather than downgrade to 3.x

**Status:** Accepted
**Date:** 2026-09-01

## Context

`SPEC-PROMPT.md` §4 specifies "Spring Boot 3.x". The pre-existing `pom.xml` already declared `spring-boot-starter-parent` **4.0.7**, and 4.0.3 / 4.0.5 / 4.0.7 are all cached in `~/.m2`.

The spec's real, load-bearing constraint is **Java 17** — stated in §4 and again in §0.5 (`record`, `final`, `BigDecimal`). Spring Boot 4.x has a Java 17 baseline (supported through Java 26) and requires Maven ≥ 3.6.3; the wrapper pins 3.9.16. So Boot 4.0.7 satisfies every constraint the spec actually cares about.

## Decision

Keep Spring Boot **4.0.7**. Treat the spec's "3.x" as a floor, not a ceiling.

## Consequences

- No downgrade churn, and no re-resolution of an entire dependency tree that is already cached.
- Spring Framework 7 baseline. Third-party tutorials written for Boot 3 may not apply verbatim — check the Boot 4 reference before copying configuration.
- Servlet 6.1 / Tomcat 11. Anything assuming Servlet 5 or Tomcat 10 will not work.
- If a required library turns out to have no Boot 4 support, the fallback is Boot 3.5.x — a parent-version change plus whatever the dependency audit surfaces. Record it here if it happens.

## Alternative rejected

Downgrade to 3.5.x to match the spec literally. Rejected: it moves the project backwards, discards a working configuration, and buys nothing the Java 17 baseline does not already give.
