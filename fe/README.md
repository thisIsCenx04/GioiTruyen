# GioiTruyen frontend

The pnpm workspace contains two Next.js App Router applications and shared
packages:

```text
apps/web          reader-facing discovery and reading experience
apps/admin        publishing, moderation, and operations console
packages/ui       shared accessible presentation components
packages/api-client typed HTTP boundary
packages/config   strict TypeScript and ESLint configuration
```

Install and verify:

```powershell
pnpm install --frozen-lockfile
pnpm typecheck
pnpm lint
pnpm test:policy
pnpm test:unit
pnpm test:coverage
pnpm build
```

Test runs are fixed to UTC, never retried, and fail on any unhandled network
request. `pnpm test:policy:self-test` proves that the policy gate rejects a
seeded flaky test fixture. After backend and frontend tests have run, use
`pnpm test:report:merge` to create one JUnit report at
`test-results/merged-junit.xml`.

Run an application:

```powershell
pnpm dev:web
pnpm dev:admin
```
