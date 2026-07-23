# Contributing

## Branch flow

```text
origin/dev → short-lived branch → origin branch → origin/dev
           → release branch → main
```

- `dev` is the protected integration branch.
- `main` contains production releases and emergency hotfixes.
- Fetch first and create every normal branch from current `origin/dev`, never
  from a potentially stale local `dev`.
- Use `<type>/<work-item>-<slug>`, for example
  `feat/PUB-003-submit-review`.
- Push the completed short-lived branch to `origin` before integrating it.
- Integrate only the reviewed, green branch into `dev`, then push `dev`.
- Never develop directly on `dev`; never force-push `dev` or `main`.
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

## Required integration sequence

```powershell
git fetch origin --prune
git switch -c feat/PLT-005-outbox-model origin/dev

# work, test and self-review
git commit -m "feat(events): persist transactional outbox messages"
git push -u origin feat/PLT-005-outbox-model

git switch dev
git fetch origin --prune
git merge --ff-only origin/dev
git merge --ff-only origin/feat/PLT-005-outbox-model
git push origin dev

git fetch origin --prune
git switch -c feat/PLT-005-outbox-worker origin/dev
```

Use the GitHub pull request/squash-merge path when branch protection requires
it. A maintainer or automation may perform the shown fast-forward integration
only after the same review and required checks pass. Delete the remote
short-lived branch after confirming `origin/dev` contains its commit.

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
