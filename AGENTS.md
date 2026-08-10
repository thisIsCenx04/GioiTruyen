# Agent Execution Rules

**Must Read First**

These rules apply to all implementation, debugging, refactoring, database, frontend, backend, and deployment work in this repository.

---

## 1. Direct Task Handling

1. Read the user's request literally and identify the concrete expected result.
2. Convert the request into a short, actionable internal task list before making changes.
3. Execute tasks one at a time in priority order.
4. Focus strictly on the stated problem. Do not expand scope unless required to make the requested feature work correctly.
5. Do not speculate when the answer can be verified from:

   * project source code;
   * configuration;
   * database schema;
   * command output;
   * logs;
   * tests;
   * build output;
   * runtime behavior.
6. Make reasonable and safe assumptions when minor details are missing, then continue.
7. Prefer the simplest working solution that follows the existing architecture.
8. Verify every material change with the smallest relevant check before moving on.
9. Stop repeated or low-value investigation once sufficient evidence exists to act.
10. Report results, errors, and blockers concisely.
11. Do not repeat the task plan after implementation.
12. Do not provide unnecessary theory unless the user explicitly asks for explanation.
13. Do not create unrelated abstractions, utilities, components, tables, endpoints, or configuration.
14. Do not rewrite working code only for stylistic preference.
15. Do not commit, push, merge, rebase, reset, or modify Git history unless explicitly requested.

---

# 2. Inspect Before Editing

Before changing implementation:

1. Inspect the relevant existing files first.
2. Identify:

   * current architecture;
   * naming conventions;
   * existing reusable components;
   * services;
   * hooks;
   * repositories;
   * DTOs;
   * validators;
   * configuration;
   * migrations;
   * routes.
3. Search for an existing implementation before creating a new one.
4. Reuse existing project infrastructure instead of introducing parallel systems.
5. Trace the complete affected flow when necessary:

```text
UI
→ frontend state/hook/service
→ HTTP client
→ API controller
→ service
→ repository
→ database
```

6. When fixing a bug, identify the actual failure point before changing code.
7. Avoid speculative fixes based only on filenames or assumptions.

---

# 3. Change Scope Control

Every change must be directly connected to the requested task.

Do not:

* refactor unrelated modules;
* rename unrelated files;
* format the whole repository;
* upgrade dependencies without necessity;
* restructure folders without necessity;
* replace an existing library because another library is preferred;
* add infrastructure that the requested task does not need.

If an adjacent issue blocks the requested task, fix only the minimum required portion and state that it was necessary.

---

# 4. Existing Architecture First

Follow the architecture already used by the project.

Prefer:

```text
existing component
existing hook
existing API client
existing DTO pattern
existing service layer
existing repository pattern
existing validation system
existing security configuration
existing error handling
existing migration structure
```

Do not introduce a second implementation path for the same concern.

Examples:

If Axios is already configured:

```text
reuse Axios instance
```

Do not create a separate `fetch` infrastructure.

If Spring Security already handles authorization:

```text
extend existing security rules
```

Do not create custom authorization middleware.

If the project uses Flyway:

```text
use Flyway migrations
```

Do not execute schema mutations manually from application startup.

---

# 5. Implementation Quality

Code must be:

* production-oriented;
* maintainable;
* explicit;
* minimal;
* consistent with the surrounding code.

Prefer small focused functions.

Avoid:

* duplicated business logic;
* deeply nested conditionals;
* unnecessary abstractions;
* excessive comments;
* large monolithic components;
* hard-coded runtime values;
* unused imports;
* dead code;
* placeholder implementations left in production code.

Comments should explain **why**, not restate what the code already says.

---

# 6. Frontend Rules

For React/Vite work:

1. Follow the existing frontend structure.
2. Reuse existing:

   * components;
   * hooks;
   * API clients;
   * state management;
   * route structure;
   * UI primitives;
   * form libraries;
   * validation libraries.
3. Do not place large feature logic directly inside `App.tsx`, page components, or route definitions.
4. Keep presentation and business logic separated when the project already follows this pattern.
5. Avoid unnecessary global state.
6. Use local/component state unless data genuinely needs broader ownership.
7. Do not perform API requests inside rendering logic.
8. Clean up:

   * event listeners;
   * timers;
   * subscriptions;
   * AbortControllers;
   * side effects.
9. Prevent duplicate requests and duplicate global listeners.
10. Preserve existing loading, error, empty, and disabled states.
11. Do not break responsive behavior.
12. Do not introduce visible UI regressions while fixing unrelated logic.
13. Do not hard-code backend URLs when the project already provides API configuration.

---

# 7. Backend Rules

For Spring Boot:

1. Preserve the existing layering.
2. Business logic belongs in services, not controllers.
3. Persistence logic belongs in repositories.
4. Controllers should primarily:

   * validate input;
   * enforce endpoint-level authorization;
   * call services;
   * return DTOs.
5. Do not expose JPA entities directly through public APIs unless the project explicitly follows that design.
6. Validate external input.
7. Reuse the existing exception and API error format.
8. Avoid unnecessary database queries.
9. Avoid N+1 query patterns.
10. Use transactions only where needed.
11. Do not silently swallow exceptions.
12. Preserve API compatibility unless the user explicitly requests a breaking change.
13. Use Java 21-compatible code.

---

# 8. API Contract Consistency

When modifying an API, verify all affected layers.

```text
Backend request DTO
Backend response DTO
Controller
Service
Frontend TypeScript type
Frontend API call
Frontend consumer
```

Do not update only one side of a frontend/backend contract.

Check:

* property names;
* nullability;
* enums;
* date formats;
* IDs;
* pagination;
* validation;
* HTTP status codes.

Prefer one canonical representation across the application.

---

# 9. Database Safety

Before changing database behavior:

1. Inspect existing entities and migrations.
2. Determine whether the requested field/table already exists.
3. Use the next valid Flyway migration version.
4. Consider:

   * existing data;
   * nullability;
   * defaults;
   * indexes;
   * foreign keys;
   * unique constraints;
   * rollback implications.
5. Avoid destructive changes unless explicitly required.

Never:

```text
DROP production data
TRUNCATE production tables
reset production database
edit an already-applied migration
```

to make development easier.

---

# 10. Bug Fixing Procedure

For bugs:

1. Reproduce or identify the failing path.
2. Inspect the relevant source.
3. Find the root cause.
4. Apply the smallest correct fix.
5. Verify the original failure no longer occurs.
6. Check nearby behavior for regressions.

Do not:

```text
change random code until the error disappears
disable validation
disable security
ignore exceptions
remove failing tests
bypass type checking
```

unless specifically required and justified.

---

# 11. Verification Policy

Verification must match the change.

Examples:

Frontend TypeScript change:

```text
typecheck
```

Frontend production-impacting change:

```text
typecheck
build
```

Backend service/controller change:

```text
relevant tests
Maven verification
```

Database migration:

```text
migration syntax
migration ordering
application startup / Flyway validation where possible
```

API contract change:

```text
backend compile
frontend typecheck
```

Do not automatically run the heaviest possible command if a smaller check provides sufficient confidence.

Before reporting completion, check for:

```text
compile errors
type errors
failed tests
broken imports
incorrect routes
migration conflicts
obvious runtime errors
```

---

# 12. Failure Handling

If a command fails:

1. Read the actual error.
2. Determine whether it is caused by:

   * the change;
   * environment;
   * missing dependency;
   * configuration;
   * unrelated existing failure.
3. Fix the underlying cause when it belongs to the requested scope.
4. Do not repeatedly rerun the same failing command without changing anything.
5. Do not hide existing failures.

If verification cannot be completed, explicitly report:

```text
what could not be verified
why
what remains affected
```

---

# 13. Security Rules

Never:

* hard-code credentials;
* expose secrets in logs;
* commit tokens;
* weaken authentication to make a feature work;
* disable authorization globally;
* accept unsafe redirect URLs without validation;
* trust client-side authorization;
* expose internal fields unnecessarily.

Validate user-controlled:

```text
URLs
IDs
uploaded files
query parameters
request bodies
sorting/filtering values
```

Authorization must be enforced on the backend even if the frontend hides the action.

---

# 14. Gioitruyen Runtime and Deployment Rules

These rules apply to all work in this repository.

## Runtime Policy

1. Do not use Docker, Docker Compose, container images, or container volumes for development, testing, staging, or production deployment.
2. Run the frontend as a normal React/Vite application with Node.js and serve its production `dist` output with the host web server.
3. Run the backend as a normal Spring Boot application with Java 21 and the packaged Maven JAR.
4. Do not restore Gradle as a build system.
5. Use native MySQL.
6. Redis and other optional services must only be enabled when their production environment variables and services are intentionally configured.
7. Do not add Docker-related files or deployment instructions unless explicitly requested.

---

# 15. Environment Policy

1. `.env` is for local development only.
2. `.env.production` is for production runtime configuration only.
3. Never hard-code:

   * credentials;
   * tokens;
   * private keys;
   * credential-bearing URLs;
   * production connection details.
4. Never commit real production secrets.
5. Keep production environment values on the server or inject them through the host process manager.
6. Backend configuration must read database, API, security, upload, and feature flags from environment variables.
7. Do not create a second configuration path for the same runtime value.
8. Frontend environment variables must follow the existing Vite configuration.
9. Do not expose backend-only secrets through `VITE_*` variables.

Before production startup verify:

```text
APP_ENV=production
SPRING_PROFILES_ACTIVE=prod
production frontend API URL
production MySQL URL
```

are loaded from `.env.production` or the production environment manager.

---

# 16. Build Artifacts

The only application artifacts copied to production are:

Frontend:

```text
fe/apps/web/dist/
```

generated by:

```powershell
pnpm --dir fe --filter @gioitruyen/web build
```

Backend:

```text
be/target/story-platform-backend-*.jar
```

generated by Maven packaging.

Database:

```text
be/src/main/resources/db/migration/
```

versioned Flyway migrations packaged with the backend.

Do not deploy:

```text
node_modules
frontend source maps unless explicitly required
be/build
Gradle output
IDE metadata
local logs
.env
test fixtures
temporary files
coverage output
```

---

# 17. Database Migration Policy

1. Every schema change must be a new forward-only Flyway migration.
2. Name migrations using the next version.

Example:

```text
V003__add_story_cover.sql
```

3. Never edit an already-applied migration.
4. Never reset, truncate, or delete a production database to make a migration pass.
5. Production starts with:

```text
MYSQL_MIGRATIONS_ENABLED=true
APP_SEED_ENABLED=false
```

6. Flyway applies migrations from the packaged backend artifact.
7. Check migration status and application readiness after deployment.
8. If a migration fails:

   * stop the rollout;
   * inspect the actual migration error;
   * create/fix the appropriate forward migration;
   * do not bypass Flyway.

---

# 18. Standard Commands

## Local Development

```powershell
pnpm dev
```

## Frontend Production Build

```powershell
pnpm --dir fe install --frozen-lockfile
pnpm --dir fe --filter @gioitruyen/web build
```

## Backend Production Build

```powershell
mvn -f be\pom.xml clean package -DskipTests
```

## Run Packaged Backend

Production environment must already be loaded by the host process manager.

```powershell
java -jar be\target\story-platform-backend-*.jar
```

Do not embed production secrets directly into these commands.

---

# 19. Production Release Checklist

1. Review the diff.
2. Verify no secrets or generated local files are included.
3. Check new migrations carefully.
4. Run frontend typecheck.
5. Run frontend production build.
6. Run relevant backend tests.
7. Run Maven verification/package.
8. Copy only:

   * frontend `dist`;
   * backend JAR;
   * reviewed migration changes contained in the backend artifact.
9. Load `.env.production` using the host process manager.
10. Start/restart the backend.
11. Verify:

```text
/api/v1/actuator/health/liveness
/api/v1/actuator/health/readiness
frontend
authentication
critical API flows
```

12. Keep the previous backend JAR and frontend `dist` available for non-destructive rollback.
13. Never roll back by:

* deleting production data;
* truncating tables;
* reversing already-applied migrations manually.

---

# 20. Time Efficiency

1. Prioritize implementation and verification over lengthy explanation.
2. Reuse existing:

   * code;
   * configuration;
   * scripts;
   * migrations;
   * seed data;
   * utilities.
3. Run independent read-only checks together when this saves time.
4. Do not ask for clarification when the repository provides a safe and clear answer.
5. Do not perform work the user did not request.
6. Avoid repeatedly opening the same files without a specific reason.
7. Search narrowly first, then expand only when necessary.
8. Stop investigation once the root cause or implementation path is sufficiently established.
9. Prefer targeted tests over full-suite execution during iteration.
10. Run broader verification only when the scope of the change justifies it.

---

# 21. Completion Standard

A task is not complete merely because code was written.

Before declaring completion:

1. Confirm requested behavior is implemented.
2. Confirm affected code compiles or typechecks where applicable.
3. Confirm relevant tests/checks pass where available.
4. Confirm frontend/backend contracts remain aligned.
5. Confirm database migrations are valid when schema changed.
6. Confirm no unrelated files were modified unintentionally.
7. Confirm no secrets were added.
8. Confirm no temporary debug code remains.

Final response should contain only useful information, typically:

```text
Implemented:
- ...

Verified:
- ...

Remaining issue:
- ...
```

Omit sections that have nothing useful to report.
