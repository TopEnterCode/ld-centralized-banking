# Centralized Banking Feature Control POC

A complete, synthetic, browser-based demonstration of centralized feature decisions for a Java banking architecture. One Spring Boot web gateway serves the experience at `http://localhost:8080`; four Java domain services call one Java DTM, and only that DTM owns the LaunchDarkly Java Server SDK.

> No real authentication, customer data, account identifiers, messages, or money movement exist in this repository.

## Start the complete Mock Mode demo

Prerequisite: Docker Desktop (Windows) or Docker Engine with Compose (Linux/macOS).

```bash
docker compose up --build
```

Open <http://localhost:8080>. Mock Mode is the credential-free default and displays a visible `MOCK MODE` badge.

Stop it with:

```bash
docker compose down
```

Convenience scripts:

```bash
./scripts/demo.sh                 # Linux/macOS
powershell -File scripts/demo.ps1 # Windows
```

## Build and test

The checked-in Maven launcher uses local Maven when available and otherwise builds a pinned Maven 3.9.11 / Java 21 / Node 22.18.0 toolchain image. Maven invokes locked `npm ci` and the TypeScript/Vite verification automatically.

```bash
./mvnw clean verify          # Linux/macOS
mvnw.cmd clean verify        # Windows
./scripts/validate.sh        # build plus SDK dependency boundary
powershell -File scripts/validate.ps1
```

Run browser E2E against the running stack:

```bash
docker compose --profile e2e run --rm e2e-tests
```

Screenshots are written to `e2e-tests/screenshots/` at 1440×900 and 1366×768.

## LaunchDarkly Live Mode

1. Copy `.env.example` to `.env` and keep `.env` untracked.
2. Set `POC_MODE=launchdarkly`, `LD_SDK_KEY`, and `LD_CLIENT_SIDE_ID`.
3. Create the flags and segments described in [LaunchDarkly setup](docs/LAUNCHDARKLY-SETUP.md), or run the preview-only bootstrap.
4. Start the same Compose stack.

```bash
docker compose up --build
```

The browser receives only `LD_CLIENT_SIDE_ID`. `LD_SDK_KEY` exists only in DTM; `LD_API_ACCESS_TOKEN` is reserved for guarded server-side tooling. Missing or invalid live credentials return typed defaults and show degraded status.

Bootstrap preview (no external writes):

```bash
./mvnw -pl tools/ld-bootstrap exec:java -Dexec.args="--preview"
mvnw.cmd -pl tools/ld-bootstrap exec:java -Dexec.args="--preview"
```

External writes require all environment variables plus both `--apply` and `--confirm APPLY`. Production-like environment keys are rejected. Review the preview and obtain explicit approval before using apply.

## What to demonstrate

- `client-new-payment-ui`: browser-side legacy/new payment experience; never authorization.
- Individual targeting: one exact synthetic key.
- `bank-employees` and `pilot-customers` segment behavior.
- Stable SHA-256 percentage rollout across `demo-user-001` … `demo-user-100`.
- Payment migration: Off → Shadow → Live → Complete.
- Payment v2 kill switch and dependency failure fallback.
- DTM timeout/unavailable and provider failure with honest sources.
- Complete Profile → Fraud → Payment → Notification timeline.

Use [the detailed Thai presenter runbook](docs/DEMO-SCRIPT-TH.md) for the exact clicks, talking points, expected results, cleanup, and Mock Mode fallback.

## Modules

| Module | Responsibility |
|---|---|
| `banking-contracts` | Immutable API records, enums, validation, personas, errors, typed flag registry |
| `banking-service-support` | REST `FlagDecisionClient`, short timeout, retry, circuit breaker, correlation filter |
| `dtm-service` | Mock/live provider boundary and the only LaunchDarkly Java Server SDK dependency |
| `payment-service` | v1/v2 migration, comparison, authority, kill switch |
| `customer-profile-service` | legacy/v2 synthetic profile |
| `fraud-service` | v1/v2 synthetic engine |
| `notification-service` | provider A/B routing and queued fallback |
| `web-gateway` | browser assets, safe runtime configuration, workflow orchestration |
| `e2e-tests` | architecture policies, Testcontainers runtime check, Playwright Java E2E/screenshots |
| `tools/ld-bootstrap` | preview-first guarded LaunchDarkly setup utility |

See [architecture](docs/ARCHITECTURE.md), [test matrix](docs/TEST-MATRIX.md), [security](docs/SECURITY.md), and [production considerations](docs/PRODUCTION-CONSIDERATIONS.md).

## Verified API baselines

Implementation follows the official [Java Server SDK](https://launchdarkly.com/docs/sdk/server-side/java), [JavaScript SDK](https://launchdarkly.com/docs/sdk/client-side/javascript), [contexts](https://launchdarkly.com/docs/home/flags/contexts), [migration](https://launchdarkly.com/docs/guides/flags/migrations), [resilience](https://launchdarkly.com/docs/guides/sdk/resilience), and [credential types](https://launchdarkly.com/docs/home/account/environment/keys) guidance reviewed on 2026-08-23.
