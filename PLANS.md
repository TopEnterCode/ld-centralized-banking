# Delivery plan — Centralized Banking Feature Control POC

Last updated: 2026-08-24

## Milestone 0 — Discovery and architecture baseline

- [x] Inspect repository and existing instructions (repository was effectively empty; no prior `AGENTS.md`).
- [x] Inspect JDK, Maven, Docker, Compose, and Node.js.
- [x] Verify current Spring Boot and LaunchDarkly SDK APIs/versions from official sources.
- [x] Record decisions and assumptions.
- [x] Create milestone plan.

Validation: repository/toolchain inventory completed. Host has Node.js 25.9.0 and Docker CLI/Compose; host Java/Maven are absent; Docker daemon was not initially running.

## Milestone 1 — Maven foundation and shared contracts

- [x] Create Maven reactor, wrapper, quality plugins, shared DTOs, errors, personas, flag registry, and correlation support.
- [x] Add architecture guard proving only DTM depends on the server SDK.
- [x] Add unit and contract tests.
- [x] Run compile/format/unit validation.

Validation: Java 21 container build completed successfully; shared-contract, resilience, architecture, and WireMock contract tests pass.

## Milestone 2 — Central DTM

- [x] Implement mock and LaunchDarkly providers behind `FeatureFlagProvider`.
- [x] Implement typed single/batch/status APIs and controlled fault injection.
- [x] Add deterministic targeting, segments, SHA-256 rollout, SDK defaults, validation, and health.
- [x] Test targeting, rollout stability/counts, invalid input, unknown flags, and provider failure.

Validation: DTM provider and controller tests pass in Mock Mode without credentials.

## Milestone 3 — Java banking services

- [x] Implement resilient DTM client abstraction with timeout/retry/circuit breaker.
- [x] Implement profile, fraud, payment migration/kill switch, and notification services.
- [x] Add local safe fallbacks and controlled dependency failures.
- [x] Test migration stages, kill switch, DTM timeout/unavailable, and contracts.

Validation: service unit tests cover all four migration stages, kill-switch routing, and local DTM fallback.

## Milestone 4 — Web gateway and browser UI

- [x] Implement Java journey orchestration and safe runtime configuration.
- [x] Implement Vite/TypeScript browser flag providers (mock and LaunchDarkly live).
- [x] Build polished single-page banking simulator, presenter controls, service map, timeline, rollout grid, architecture, and explanation views.
- [x] Validate frontend build and secret exclusion.

Validation: TypeScript checking, Vite production bundle, npm audit, runtime secret-boundary test, and backend contract test pass.

## Milestone 5 — Packaging, operations, and documentation

- [x] Add Dockerfile, Docker Compose, scripts, environment sample, and bootstrap preview/apply guardrails.
- [x] Complete architecture, setup, security, production, test matrix, and Thai demo documentation.
- [x] Run Maven package/verify and Docker Compose smoke test.

## Milestone 6 — E2E and visual QA

- [x] Add Playwright Java scenarios and screenshots.
- [x] Start full stack and exercise all presenter controls.
- [x] Inspect 1440×900 and 1366×768 layouts; fix clipping, contrast, overflow, and states.
- [x] Re-run complete validation and record exact evidence.

## Completion record

All milestones are implemented and validated. Final evidence on 2026-08-24: the 11-module Maven reactor, Spotless, PMD, TypeScript, Vite production build, dependency-boundary validation, Docker Compose health checks, six Playwright browser scenarios, two required viewport screenshots, and the Java 21 runtime check all pass.
