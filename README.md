# GioiTruyen

## Local development without Docker

The local runtime uses native MySQL, Spring Boot, and Next.js. Start MySQL and
the backend from the repository root:

```powershell
.\scripts\run-local-backend.ps1
```

In another terminal, start the reader application:

```powershell
pnpm --dir frontend dev:web
```

The local profile runs Flyway migrations and development data seeding against
the `gioitruyen` MySQL database. Redis health checks are disabled for this
profile.

## Local quality gates

Use Java 21, Node.js 24, and pnpm 11.17.0. CI runs the same commands and
rejects changes that fail formatting/static analysis, tests, coverage, builds,
or the OpenAPI contract.

```powershell
.\backend\gradlew.bat --project-dir backend clean check bootJar
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test:policy
pnpm --dir frontend test:policy:self-test
pnpm --dir frontend test:coverage
pnpm --dir frontend build
pnpm --dir frontend api:lint
pnpm --dir frontend api:gate:self-test
pnpm --dir frontend api:bundle
```

Security gates, scanner versions, checksum verification, SBOM retention, and
the exception process are documented in
[`docs/security/ci-security-gates.md`](docs/security/ci-security-gates.md).
