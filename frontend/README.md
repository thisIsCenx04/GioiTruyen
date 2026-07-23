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
pnpm build
```

Run an application:

```powershell
pnpm dev:web
pnpm dev:admin
```
