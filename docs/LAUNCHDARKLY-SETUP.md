# LaunchDarkly setup

Mock Mode needs none of this configuration. Do not use production data or a production environment for the POC.

## Credentials and ownership

| Value | Consumer | Browser-visible? |
|---|---|---|
| Client-side ID | browser JS SDK | Yes, by design |
| SDK key | DTM Java Server SDK | Never |
| API access token | guarded Java bootstrap/admin tooling | Never |

This POC is linked to project `centrailized-banking` and non-production environment `devolopment`. These are existing LaunchDarkly keys, so preserve their spelling and lower-case kebab-case form.

## Preview the desired setup

Linux/macOS:

```bash
./mvnw -pl tools/ld-bootstrap exec:java -Dexec.args="--preview"
```

Windows:

```powershell
mvnw.cmd -pl tools/ld-bootstrap exec:java -Dexec.args="--preview"
```

Preview is the default and makes no external request. Apply is intentionally not part of normal setup. It requires explicit approval, `LD_API_ACCESS_TOKEN`, `LD_SDK_KEY`, `LD_CLIENT_SIDE_ID`, a non-production key, and `--apply --confirm APPLY`.

## Flags

Create the variations below and keep off variations equal to the safe fallbacks.

| Key | Kind | Variations | Off/default | Browser SDK availability |
|---|---|---|---|---|
| `client-new-payment-ui` | Boolean | false, true | false | Enable using Client-side ID |
| `profile-response-v2` | Boolean | false, true | false | Disabled |
| `payment-api-migration` | String | off, shadow, live, complete | off | Disabled |
| `payment-v2-enabled` | Boolean | false, true | false | Disabled |
| `fraud-engine-version` | String | v1, v2 | v1 | Disabled |
| `notification-provider` | String | provider-a, provider-b | provider-a | Disabled |
| `maintenance-banner` | Reserved | Not bootstrapped | n/a | No application consumer yet |

Only `client-new-payment-ui` is required by a browser SDK. Do not enable the server flags for client-side SDKs.

## Multi-context attributes

DTM sends:

- `user`: key, employee, cohort, tier, region, channel;
- `device`: key, platform, appVersion.

All values are synthetic. If context privacy policies become stricter, mark non-rule attributes private or enable all-attributes-private and explicitly allow only rule fields.

## Segments

Create:

1. `bank-employees`: rule `user.employee` equals `true`.
2. `pilot-customers`: rule `user.cohort` equals `pilot`.

Suggested rule order for Boolean/v2 flags:

1. exact context key for individual demonstration → new/v2 variation;
2. `bank-employees` segment → new/v2 variation;
3. `pilot-customers` segment → new/v2 variation;
4. percentage rollout by `user.key` → new/v2 versus safe fallback;
5. fallthrough → safe fallback.

Use the same attribute and context kind for all percentage rules so assignments stay stable.

## Start Live Mode

Create an untracked `.env`:

```dotenv
POC_MODE=launchdarkly
LD_SDK_KEY=...
LD_CLIENT_SIDE_ID=...
LD_API_ACCESS_TOKEN=...
LD_PROJECT_KEY=centrailized-banking
LD_ENVIRONMENT_KEY=devolopment
ENABLE_LD_ADMIN_CONTROLS=false
```

Then:

```bash
docker compose up --build
```

Expected UI badge: `LAUNCHDARKLY LIVE`. Invalid/missing credentials show a degraded connection and serve typed defaults. Runtime presenter controls do not write to the LaunchDarkly account; use the guarded bootstrap after review and explicit approval.
