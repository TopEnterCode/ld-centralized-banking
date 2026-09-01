# Architecture decisions and assumptions

## Recorded 2026-08-23

1. **Blank-repository baseline.** The working directory contained only an empty VS Code settings file and was not a Git repository. There is no legacy implementation to preserve.
2. **Reproducible toolchain.** Java is compiled at release 17 and containers run Eclipse Temurin Java 21. The Maven launcher can use installed Maven or a pinned Docker build environment containing Maven 3.9.11, Java 21, and Node 22.18.0. Maven itself runs locked frontend install/type-check/build executions. This avoids platform-specific Node unpack behavior while preserving one-command reproducibility.
3. **Dependency versions.** Spring Boot 3.5.16 is the current 3.5.x line documented by Spring. LaunchDarkly Java Server SDK 7.15.0 and `@launchdarkly/js-client-sdk` 4.9.4 are used based on current official documentation/package metadata checked on 2026-08-23.
4. **Typed migration string.** `payment-api-migration` is implemented as a typed string with `off`, `shadow`, `live`, and `complete`. Native migration flags are not assumed because they require account-side flag configuration and capability; the POC implements the same call/compare/authoritative semantics and documents the distinction.
5. **Mock mode precedence.** Presenter rules are applied in this order: explicit individual target, enabled employee segment, enabled pilot segment, then stable percentage rollout, then the flag's mock default. This makes demonstrations deterministic and reasons explainable.
6. **Rollout hashing.** Mock rollout uses SHA-256 over `flagKey + ':' + contextKey`, interprets the first eight bytes as an unsigned value, and maps it into buckets 0–99. The same flag/context pair is stable across processes and restarts.
7. **Client flag scope.** Only `client-new-payment-ui` and `client-new-home-experience` are made available to the browser SDK. Mock browser evaluation is performed by an isolated TypeScript provider using state supplied by the gateway. All payment validation and authority stay in Java.
8. **DTM transport.** Banking services depend on a `FlagDecisionClient` Java interface in a shared support module. REST is the POC adapter; callers are insulated from a future gRPC replacement.
9. **Failure semantics.** Provider failure returns the typed registry default with source `sdk-default`; inability to reach DTM returns each service's local fallback with source `service-fallback`. Neither is labeled as LaunchDarkly.
10. **Synthetic-only data.** All persons, recipients, account aliases, payment references, and balances are generated demo values and never resemble real banking credentials or identities.
11. **Admin writes.** LaunchDarkly bootstrap defaults to preview. Live mutations require `--apply`, an explicit confirmation token, admin enablement, API token, and a non-production environment. This delivery will not write to an external account.
12. **Host constraint.** Docker Desktop was not running during discovery. Docker smoke and browser QA remain planned after attempting to start the authorized local Docker environment; any unexecutable check will be reported exactly, never claimed as passed.
