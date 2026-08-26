# Test matrix

| # | Scenario | Primary automated evidence | Expected |
|---:|---|---|---|
| 1 | Client flag legacy/new UI | Playwright `clientFlagIndividualAndSegmentsChangeBrowserUi` | chip and new panel change |
| 2 | Individual user | DTM unit + Playwright | exact key only |
| 3 | Employee segment | DTM unit + Playwright | employee true gets new variation |
| 4 | Pilot segment | DTM unit + Playwright | cohort pilot gets new variation |
| 5 | Stable 10% rollout | DTM unit + Playwright | repeat count/assignments stable |
| 6 | Stable 50% rollout | DTM unit + Playwright | stable broader cohort |
| 7 | All backend services call DTM | journey timeline E2E | five flag decisions across four services |
| 8 | Migration off | Payment unit + E2E | v1 only, v1 authoritative |
| 9 | Migration shadow | Payment unit + E2E | v1+v2, return v1, compare |
| 10 | Migration live | Payment unit + E2E | v1+v2, return v2, compare |
| 11 | Migration complete | Payment unit + E2E | v2 only, v2 authoritative |
| 12 | Kill switch | Payment unit + E2E | v1 authoritative |
| 13 | DTM timeout | Playwright | service-fallback, valid journey |
| 14 | DTM unavailable | Playwright | service-fallback, valid journey |
| 15 | Provider failure | DTM unit + Playwright | sdk-default and degraded |
| 16 | Missing credentials | default profile/build | Mock Mode |
| 17 | Unknown flag | `FlagDecisionServiceTest` | rejected |
| 18 | Invalid context | Bean Validation / controller contract | HTTP 400 problem response |
| 19 | Same rollout assignment | `MockFeatureFlagProviderTest` | same SHA-256 bucket |
| 20 | Full journey | Playwright migration test | success + six timeline rows |
| 21 | Browser has no SDK key | `ArchitecturePolicyTest` | no match |
| 22 | Browser has no API token | `ArchitecturePolicyTest` | no match |
| 23 | Only DTM has server SDK | `ArchitecturePolicyTest`, validation scripts | no dependency elsewhere |
| 24 | Java 21 runtime image | Testcontainers test and Docker image | Java 21 |
| 25 | Required viewports | Playwright screenshot test | no horizontal overflow |

## Test layers

- Unit: bucketing, mock targeting/segments, provider failure, migration/kill-switch/failure paths, contract models.
- Contract: Bean Validation, typed flag/type rejection, stable response records, RFC-style errors.
- Integration: resilient DTM client uses an unreachable endpoint to prove typed local fallback; WireMock-compatible gateway clients isolate REST dependencies.
- Container: Java 21 production base check and full Docker Compose health/smoke.
- Browser: Playwright Java drives visible controls, journey, timeline, grid, and screenshots.

No check may be reported as passing unless its command actually ran. If Docker is unavailable, container and browser tests are recorded as blocked while unit/architecture/frontend build remain independently verifiable.

