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
