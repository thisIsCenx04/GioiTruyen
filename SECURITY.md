# Security Policy

## Reporting

Do not open a public issue for a suspected vulnerability, exposed credential,
personal data leak or financial-integrity problem.

Use the repository's private GitHub Security Advisory channel. If private
advisories are unavailable, contact the repository owner through a private,
authenticated channel and include only the minimum information required to
establish contact.

Do not include production credentials, access tokens, personal data, bank
details or exploit payloads in normal issues, pull requests or chat.

## Initial response targets

| Severity | Acknowledge | Containment target |
|---|---:|---:|
| Critical | 4 hours | 24 hours |
| High | 1 business day | 3 business days |
| Medium | 3 business days | planned release |
| Low | 5 business days | backlog review |

Targets are operational goals, not a guarantee.

## Repository security rules

- Real `.env` files and secret directories are ignored.
- CI and production must use managed secrets and short-lived identities.
- Committed credentials must be revoked and rotated; deleting Git history alone
  is not remediation.
- Auth, authorization, ledger, top-up, donation and withdrawal changes require
  security-focused review and regression tests.
- Security fixes are merged into `main` and synchronized back to `dev`.

## Supported versions

Until the first production release, only the current `dev` integration line is
supported. After release, supported versions will be listed here.
