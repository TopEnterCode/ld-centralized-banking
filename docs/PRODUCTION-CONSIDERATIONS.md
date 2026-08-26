# Production considerations

## DTM high availability and load balancing

Run at least three stateless DTM instances across failure zones behind an internal load balancer. Each instance keeps one long-lived `LDClient`. Use readiness that distinguishes process health from SDK data readiness; do not remove an otherwise safe instance solely because LaunchDarkly streaming is temporarily reconnecting.

## Additional network hop

Central DTM provides governance and uniform metadata but adds latency and a failure boundary. Measure p50/p95/p99 decision latency at domain services and end-to-end. Co-locate DTM with callers, keep payloads compact, reuse connections, batch only when it reduces total latency, and define a latency budget before adoption.

## Timeout, retry, circuit breaker, and local fallback

Timeout must be shorter than the caller's remaining request budget. Retry only brief transient failures, add jitter, and avoid retry storms. Tune circuit breakers per caller and flag class. Every domain must own a tested local fallback: Payment v1, legacy Profile, Fraud v1, and Provider A/queued Notification. Alert on fallback rate and circuit state, not just HTTP errors.

## SDK wrapper alternative

For latency-sensitive services, an approved internal Java SDK wrapper can replace the DTM hop while preserving the typed registry, context policy, logging, and fallback semantics. The tradeoff is distributing SDK lifecycle/configuration and increasing upgrade coordination. Compare this against central DTM using measured SLOs and organizational ownership.

## Relay Proxy option

LaunchDarkly Relay Proxy can reduce outbound connections, centralize network access, and support daemon/offline topologies. It does not replace typed application fallbacks or domain-level circuit breakers. Operate it highly available, capacity test stream fan-out, secure SDK-key handling, and monitor data freshness.

## Privacy

Perform data classification before sending any context attribute. Prefer opaque stable keys, omit direct identifiers, mark private attributes, define retention, and validate regional/data residency requirements. Never send account, card, national-ID, email, phone, or transaction narrative values as contexts.

## Credential separation

Store SDK keys, client-side IDs, and API tokens as distinct secrets with least privilege, rotation, and environment isolation. API tokens must never enter runtime browser configuration. Prevent production credentials in development through policy and secret scanning.

## Monitoring

Capture evaluation counts, reasons, source, fallback status, DTM latency, timeout rate, circuit transitions, provider initialization/data-source status, stream age, migration comparison mismatches, and kill-switch changes. Correlate them with transaction traces without recording sensitive context.

## Capacity testing

Test normal and burst evaluation load, 100% rollout changes, reconnect storms, DTM loss, LaunchDarkly loss, slow DNS, load-balancer failover, and graceful shutdown. Size thread/connection pools for the extra hop and verify memory/CPU impact of SDK state. Prove fallbacks under peak load and measure recovery after circuits half-open.

## Operational governance

Define owners, expiry dates, flag naming, change approval, rollback responsibility, audit review, stale-flag removal, segment ownership, and emergency procedures. Treat migration stages as a controlled state machine with comparison-quality gates rather than manual toggles alone.

