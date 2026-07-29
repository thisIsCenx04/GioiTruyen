# Story Platform load suite

This suite exercises the production API contract without provisioning or
mutating infrastructure. Run it only against an isolated, production-like
environment populated with disposable seed data.

## Prerequisites

- k6 0.49 or newer
- a reachable Story Platform API
- published `STORY_ID`, `CHAPTER_ID`, and active `TEAM_ID` UUIDs
- an optional disposable `BEARER_TOKEN` to include private wallet reads

Never use production credentials or real user wallets. The suite starts
anonymous reading sessions and can create a high volume of test records.

## Profiles

| Profile | Purpose | Default shape |
|---|---|---|
| `smoke` | Validate target and seed data | 10 RPS for 30 seconds |
| `ramp` | Normal MVP peak | 5m ramp, 20m at 300 RPS, 5m down |
| `spike` | New-chapter burst and recovery | 300 → 1,500 → 300 RPS |
| `soak` | Leak, pool and queue stability | 300 RPS for 8 hours |
| `cold-cache` | Cache miss/stampede behavior | 300 synchronized VUs |
| `degraded` | Backpressure during an externally injected dependency fault | 100 RPS for 10 minutes |

The mixed profile follows the blueprint traffic split: 70% public catalog and
reading GETs, 15% reading session writes, 8% search/rankings, 5% community,
and 2% team/private wallet reads.

## Run

PowerShell:

```powershell
$env:BASE_URL = 'https://staging.example/api/v1'
$env:STORY_ID = '00000000-0000-4000-8000-000000000001'
$env:CHAPTER_ID = '00000000-0000-4000-8000-000000000002'
$env:TEAM_ID = '00000000-0000-4000-8000-000000000003'
$env:PROFILE = 'smoke'
k6 run performance/k6/story-platform.js
```

Run `smoke` first. Promote sequentially to `ramp`, `spike`, `cold-cache`, and
`soak` only when the previous report passes. Override `TARGET_RPS`,
`SPIKE_MULTIPLIER`, `SOAK_DURATION`, or `SMOKE_DURATION` when testing a
different capacity tier.

For `degraded`, start the test and inject a MySQL/Redis failure using the
environment owner's approved procedure. The suite accepts controlled
backpressure only when HTTP 429/503 includes `Retry-After`; it does not mutate
infrastructure itself.

## Gates and evidence

- cached public GET p95 < 150 ms;
- uncached public GET p95 < 300 ms;
- search/ranking p95 < 500 ms;
- writes p95 < 500 ms;
- wallet p95 < 700 ms;
- HTTP/check and 5xx error rates < 0.1%;
- p99 is capped at twice each p95 budget.

Save k6 JSON and summary output outside Git. Correlate the run window with
MySQL connections/replication lag, Redis hit/eviction, JVM saturation, and
outbox oldest age before accepting the capacity result.

Validate the deterministic profile configuration without k6:

```powershell
node --test performance/test/profile-config.test.mjs
node --check performance/k6/story-platform.js
```
