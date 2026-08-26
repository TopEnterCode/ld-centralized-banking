# POC security boundaries

## Deliberate limitations

This is a synthetic demonstration, not a banking product. It has no authentication, authorization, account lookup, payment rail, persistence, customer messaging, or fraud decision authority. Never connect it to production systems or enter real information.

Feature flags are not security controls. `client-new-payment-ui` changes presentation only. Java validates every request and the server-side workflow remains authoritative.

## Credential separation

- Browser: may receive only the environment Client-side ID.
- DTM: receives only `LD_SDK_KEY`; the key is never serialized or logged.
- Bootstrap tooling only: may receive `LD_API_ACCESS_TOKEN`; runtime containers do not receive it.
- `.env` is ignored. `.env.example` contains empty placeholders.

Architecture tests scan browser sources and module dependencies. Only `dtm-service/pom.xml` may contain `launchdarkly-java-server-sdk`.

## Input and data controls

- Bean Validation constrains context keys, versions, platform, aliases, and amounts.
- Unknown flags return not-found; wrong requested types return unprocessable entity.
- Correlation IDs accept only a short safe character set or are regenerated.
- Every persona and `demo-user-001`…`100` is synthetic.
- Error responses do not include stack traces, credentials, or upstream bodies.

## Admin changes

Preview is the default. Apply requires a non-production environment plus `--apply --confirm APPLY` and required credentials. Runtime account mutations are intentionally not performed by the browser control plane. Never enable admin controls on a production-like environment.

## Before any production adaptation

Add real identity/authentication and authorization outside feature flags, CSRF protection where browser sessions exist, TLS/mTLS, secret manager injection, network policy, rate limits, audit logging, dependency/SBOM scanning, data classification, threat modeling, penetration testing, and independent payment-control review.
