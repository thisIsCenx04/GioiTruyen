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
.\gradlew.bat test
```

The application starts deny-by-default. Only Actuator health/info are public
until Identity and explicit API authorization policies are implemented.

## Package boundaries

Each business package is a module boundary. Within a module, implementation
follows:

```text
presentation → application → domain
infrastructure → application + domain
```

Architecture tests will enforce these dependencies in the next Phase Base
commit.
