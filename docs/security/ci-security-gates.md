# CI security gates

Every pull request to `dev` or `main`, every push to those branches, and the
weekly scheduled run execute the security workflow.

## Blocking policy

- Gitleaks scans complete Git history with redacted reports. A deterministic
  negative-control test must also prove that the configured rule exits with a
  blocking status.
- CodeQL analyzes Java and JavaScript/TypeScript with the extended security and
  quality query suites.
- Trivy scans packaged Java dependencies, lockfiles, repository secrets,
  Dockerfile/IaC configuration, and the built runtime image.
- A structured Trivy license report blocks unreviewed high/critical restricted
  licenses. The only reviewed exception is the optional Windows Sharp binary's
  combined Apache-2.0/LGPL-3.0-or-later metadata; it is not present in the Linux
  runtime image.
- High or critical resolved dependency, IaC, and image findings block the
  workflow. Critical/high unresolved findings require a documented,
  time-limited security exception with owner and expiry; they are never hidden
  through a source-code ignore added only to make CI pass.
- CycloneDX SBOMs are retained as immutable workflow artifacts named with the
  commit SHA.
- Pull requests also run GitHub dependency review and reject newly introduced
  high or critical vulnerabilities in runtime, development, or unknown scope.

## Supply-chain controls

GitHub Actions are pinned to full commit SHAs with the reviewed release in a
comment, and a repository policy script rejects mutable references. Gitleaks
and Trivy binaries are pinned to exact versions and verified
against hard-coded SHA-256 digests before execution. The distroless Java base is
pinned by immutable digest, the runtime runs as non-root UID/GID `65532`, and
the build context contains only the packaged application JAR.

Dependabot opens weekly grouped updates against `dev`. Updates still pass every
quality and security gate before they can be merged.

The root pnpm override keeps Next.js' optional `sharp` runtime on the first
non-vulnerable `0.35.x` line until Next.js updates its exact transitive pin.
Frontend unit tests and production builds validate the compatibility override.

## Local negative control

Install Gitleaks `8.30.1`, then point the self-test at the binary:

```powershell
$env:GITLEAKS_BIN = "C:\path\to\gitleaks.exe"
pnpm --dir frontend security:gate:self-test
```

The script constructs a synthetic marker only in an OS temporary directory,
expects Gitleaks exit code `17`, verifies the dedicated rule ID, and removes the
directory in a `finally` block. It does not contain or log a real credential.
