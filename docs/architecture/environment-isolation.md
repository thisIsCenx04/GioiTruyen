# Environment isolation decision

## Decision

Dev and staging are separate AWS accounts built from one versioned OpenTofu
module. Each environment owns distinct state, KMS keys, network ranges,
registries, logs, identities and secret references. Production will consume the
same module only after a separate production-readiness review.

The runtime baseline is ECS Fargate in private subnets. Public IP assignment and
interactive ECS Exec are disabled. Edge ingress is intentionally absent until
the Cloudflare/TLS policy commit, so applying this baseline cannot expose the
application directly to the internet.

## Security boundaries

| Boundary | Enforcement |
| --- | --- |
| Wrong account | AWS provider `allowed_account_ids` |
| State crossover | Dedicated S3 bucket, KMS key and state path |
| Network crossover | Distinct VPC and non-overlapping CIDR |
| Identity crossover | Per-environment and per-runtime IAM roles |
| CI credentials | GitHub OIDC subject bound to repository + environment |
| Artifact drift | ECR immutable tags and required image digest |
| Secret disclosure | Secret resources only; no secret versions in IaC |
| Runtime breakout | Non-root, read-only filesystem, dropped capabilities |
| Accidental exposure | Private subnets, no public task IP, no ingress rule |

Staging has the same two-AZ layout as dev but uses one NAT gateway per
availability zone, longer logs and a longer secret recovery window. Dev reduces
NAT cost while retaining the same module and security controls.

## Operational consequences

- Account vending, billing guardrails and access federation must exist before
  the first real plan.
- The state bootstrap is intentionally separate because an S3 backend cannot
  create itself.
- Runtime services remain at zero tasks until secret values, data connectivity,
  edge ingress and an immutable image exist.
- C024–C027 extend the exported private subnet and runtime security-group
  contracts instead of creating parallel networks.
- Cloud resources are not created by CI on feature branches; CI only validates,
  plans with mocked providers and runs policy scans.
