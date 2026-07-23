# Story Platform Backend

Java 21 and Spring Boot backend organized as a clean layered modular monolith.

## Requirements

- JDK 21
- Root `.env` created from `.env.example`
- MongoDB replica set and Redis for runtime integration

Load the root environment and run:

```powershell
. ..\scripts\load-env.ps1
.\gradlew.bat bootRun
```

Run tests:

```powershell
.\gradlew.bat unitTest
.\gradlew.bat test
.\gradlew.bat jacocoTestReport jacocoTestCoverageVerification
.\gradlew.bat check
```

`unitTest` only discovers tests below `com.storyplatform.unit`, does not start
Spring, and must not access network, MongoDB, or Redis. The JaCoCo HTML report
is written to `build/reports/jacoco/test/html/index.html`.

MongoDB integration tests use Testcontainers with a pinned MongoDB replica-set
image. They run automatically when a Docker-compatible runtime is available and
are reported as skipped when the runtime is absent; CI must provide Docker and
must not accept that skip.

## MongoDB migrations

Migrations are forward-only, versioned, checksummed and protected by a MongoDB
lease lock. They are disabled during normal API startup and default to dry-run
when explicitly enabled.

Preview pending migrations:

```powershell
$env:MONGODB_MIGRATIONS_ENABLED = "true"
$env:MONGODB_MIGRATIONS_DRY_RUN = "true"
.\gradlew.bat bootRun --args="--spring.main.web-application-type=none"
```

Apply after reviewing the dry-run with a dedicated migration database identity:

```powershell
$env:MONGODB_MIGRATIONS_DRY_RUN = "false"
.\gradlew.bat bootRun --args="--spring.main.web-application-type=none"
```

Never edit an applied migration. Add a higher version that is idempotent and
backward-compatible with the previous application version. Destructive changes
follow expand, backfill, switch and contract as separate releases.

## Transactional outbox

Cross-module events use a versioned envelope with event, aggregate, correlation
and optional actor/Team metadata. Event payloads are JSON with a 64 KiB default
limit and must contain only the minimum consumer data—never credentials, tokens,
raw payment data or unnecessary PII.

Call `OutboxAppender.append(...)` from an existing MongoDB transaction that also
writes the business aggregate. The appender deliberately uses mandatory
transaction propagation and rejects standalone calls.

The polling worker is disabled by default. A dedicated worker deployment enables
`OUTBOX_WORKER_ENABLED=true`; it atomically claims messages with an expiring
lease, fans out to version-specific consumers, retries with bounded exponential
backoff and moves terminal failures to `DEAD_LETTER`. Inbox receipts deduplicate
each `(consumer, eventId)` transactionally. External handlers must also pass the
event ID as the provider idempotency key.

## Observability

Actuator and Micrometer instrument HTTP, JVM, MongoDB and Redis. The outbox adds
the low-cardinality observations `story.outbox.poll` and
`story.outbox.process`; they never include event IDs, actor/Team IDs, payloads,
exception messages or credentials.

Console logs use structured ECS JSON and include trace/span/correlation context.
Application code must log allowlisted fields, stable error codes and sanitized
values only. Never pass request bodies, connection strings, tokens, private
chapter content or raw exception messages to a logger or telemetry attribute.

OTLP export is deny-by-default for local and test runs. Set
`OTEL_TRACES_EXPORT_ENABLED=true` and/or
`OTEL_METRICS_EXPORT_ENABLED=true` only in an environment with an approved
collector. `OTEL_EXPORTER_OTLP_ENDPOINT` is the collector base URL; configure
collector authentication through the runtime secret manager, never `.env`
committed to Git.

The application starts deny-by-default. Only Actuator health/info are public
until Identity and explicit API authorization policies are implemented.

## Package boundaries

Each business package is a module boundary. Within a module, implementation
follows:

```text
presentation → application → domain
infrastructure → application + domain
```

ArchUnit tests enforce module isolation, dependency direction, framework-free
domain code, and top-level cycle checks.
