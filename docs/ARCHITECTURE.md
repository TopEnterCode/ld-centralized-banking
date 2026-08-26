# Architecture

## Runtime topology

```text
Browser (minimal TypeScript)
  ├─ client-new-payment-ui ───────────────> LaunchDarkly client endpoint (Live only)
  └─ HTTPS/HTTP :8080 ────────────────────> Web Gateway (Spring Boot)
                                                │
                 ┌──────────────────────────────┼──────────────────────────────┐
                 v                              v                              v
        Customer Profile                  Fraud Service                 Payment Service
                 │                              │                              │
                 └──────────────┬───────────────┴───────────────┬──────────────┘
                                v                               v
                         Central DTM <──────────────── Notification Service
                                │
                                └── singleton LDClient ────────> LaunchDarkly
```

Only web gateway maps a host port. `dtm-service` and all domain services remain on the Compose network.

## Decision path

1. Gateway creates one correlation ID for the synthetic journey.
2. Profile requests `profile-response-v2` from DTM.
3. Fraud requests `fraud-engine-version` from DTM.
4. Payment requests `payment-api-migration` and `payment-v2-enabled` from DTM.
5. Notification requests `notification-provider` from DTM.
6. Each domain response contains safe decision metadata; gateway turns it into the visible timeline.
7. Browser `client-new-payment-ui` changes presentation only. Java still validates the request and owns the workflow.

## DTM design

`FeatureFlagProvider` has two implementations:

- `MockFeatureFlagProvider` is active for `mock` and `test`. Its state is in-process and deterministic. Audience precedence is individual → employee segment → pilot segment → percentage bucket → default.
- `LaunchDarklyFeatureFlagProvider` is active for `launchdarkly`. It converts synthetic data into a `user` + `device` multi-context and returns `EvaluationDetail` reason/source metadata.

Live configuration creates one `LDClient` bean with a bounded initialization wait. Spring closes it during graceful shutdown. The SDK maintains its own in-memory flag data; evaluations do not synchronously call LaunchDarkly.

The DTM registry rejects unknown keys and requested type mismatches before provider evaluation. Allowed sources are `launchdarkly`, `mock`, `sdk-default`, and `service-fallback`.

## Resilience boundary

`FlagDecisionClient` is a Java interface. `ResilientDtmClient` is the current REST adapter and applies:

- configurable connect/read timeout (`DTM_TIMEOUT_MS`, default 700 ms),
- one short retry for transient failures,
- count-based circuit breaker with a three-second open interval,
- the registered typed fallback returned with `service-fallback`.

This interface allows a future gRPC transport without changing domain services. Gateway also has bounded internal-service calls and synthetic last-resort responses to keep the presenter journey valid.

## API migration state machine

| Stage | Calls | Authoritative | Comparison |
|---|---|---|---|
| off | v1 | v1 | none |
| shadow | v1 + v2 | v1 | yes |
| live | v1 + v2 | v2 | yes |
| complete | v2 | v2 | none |

If `payment-v2-enabled=false`, v1 is authoritative regardless of migration stage. If v2 fails, v1 is used and the response is degraded.

The Java SDK supports native migration APIs, but this POC uses a typed string because native migration-flag account capability and configuration cannot be assumed. The observable call/compare/authority behavior is complete; native telemetry is the documented difference.

## Browser boundary

`web-gateway/frontend` contains all browser code. `BrowserFlagProvider` isolates:

- a Mock adapter that calls the safe gateway flag endpoint and never claims LaunchDarkly as source;
- a Live adapter using `@launchdarkly/js-client-sdk`, a three-second initialization timeout, multi-context identification, and `change:client-new-payment-ui` subscription.

The runtime endpoint returns the client-side ID only in live mode and lists only `client-new-payment-ui`. SDK keys and API tokens have no serializer or browser path.

