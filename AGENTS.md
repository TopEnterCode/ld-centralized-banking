# Repository working agreement

- This is a Java 17 source / Java 21 runtime Maven multi-module project.
- Keep the LaunchDarkly Java Server SDK dependency and SDK-key handling in `dtm-service` only.
- Keep browser code isolated in `web-gateway/frontend`; it may receive only the client-side ID.
- Never add real customer data, credentials, account numbers, card numbers, emails, phone numbers, or national IDs.
- Mock mode is the default and must remain deterministic and credential-free.
- Preserve safe fallbacks: payment v1, legacy profile, fraud v1, notification provider A/queued.
- Run `./mvnw spotless:check verify` (or `mvnw.cmd spotless:check verify`) after Java changes.
- Run `scripts/validate.sh` or `scripts/validate.ps1` before handoff.
- Do not write to LaunchDarkly without `--apply`, non-production validation, and explicit confirmation.
- Do not commit changes unless the user asks.

