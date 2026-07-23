# Contributing

## Branch flow

```text
short-lived branch → pull request → dev → release branch → main
```

- `dev` is the protected integration branch.
- `main` contains production releases and emergency hotfixes.
- Create feature branches from current `dev`.
- Use `<type>/<work-item>-<slug>`, for example
  `feat/PUB-003-submit-review`.
- Never push directly or force-push to `dev` or `main`.
- Hotfixes start from the production tag/`main` and must be synchronized back
  into `dev`.

## Commit and pull request

- Use Conventional Commits:
  `feat(publishing): submit frozen revisions for review`.
- Keep one outcome per pull request.
- Production code and its unit tests belong in the same commit.
- Squash merge into `dev`; the PR title becomes the merge commit.
- Auth, security, infrastructure and monetization changes require two reviewers.
- Do not bypass required checks.

## Local environment

Copy `.env.example` to the ignored root `.env`, then load and validate it:

```powershell
. .\scripts\load-env.ps1
.\scripts\validate-env.ps1
```

Never commit or share `.env`.

## Required checks

- format, lint and typecheck;
- backend and frontend unit tests with coverage;
- architecture and affected integration/contract tests;
- OpenAPI lint/breaking-change check;
- secret, SAST, dependency, license, IaC and container scans as applicable.

See `docs/ENVIRONMENT.md` and the architecture blueprint for the complete
engineering and security rules.
