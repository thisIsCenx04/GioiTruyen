# Global environment configuration

The repository uses the root `.env` as the single local-development source for
shared backend, web, admin and infrastructure variables.

## Files

- `.env`: local values; ignored by Git.
- `.env.example`: committed variable contract without real secrets.
- `scripts/load-env.ps1`: safe PowerShell parser that does not execute values.
- `scripts/load-env.sh`: POSIX loader for trusted local `.env` files.

## PowerShell

Dot-source the loader so variables remain available in the current terminal:

```powershell
. .\scripts\load-env.ps1
```

Existing process variables win by default. Use `-Override` only for local
development when the `.env` values must replace them:

```powershell
. .\scripts\load-env.ps1 -Override
```

## POSIX shell

Source the loader:

```sh
. ./scripts/load-env.sh
```

The POSIX loader sources `.env`; only use a repository-local file you trust.

## Security rules

- Never place production credentials in `.env`.
- Never send `.env` through chat, email, issue attachments or screenshots.
- Use synthetic/local credentials only.
- Production and CI use a managed secret store and short-lived workload identity.
- When a variable is added or removed, update `.env.example` and environment
  validation in the same commit.
- Spring Boot and Next.js receive variables from the process. Only variables
  explicitly prefixed with `NEXT_PUBLIC_` may be exposed to browser bundles.
- Cloudinary API secret, Cloudflare token, JWT key and encryption key are always
  server-only.
