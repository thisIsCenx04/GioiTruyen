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
