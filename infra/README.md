# Isolated dev and staging infrastructure

This directory defines the C023 AWS baseline with OpenTofu. It does not apply
infrastructure automatically. Dev and staging use the same reusable module but
must run in different AWS accounts, remote-state buckets, KMS keys, VPC CIDRs,
service identities, secrets and GitHub environments.

## Architecture

- Two availability zones and public/private subnet tiers per environment.
- ECS Fargate services have no public IP and start at desired count zero until
  an immutable image digest and managed secret values are ready.
- Dev uses one NAT gateway to control cost. Staging uses one per availability
  zone to preserve the production-like failure topology.
- ECR repositories are KMS encrypted, immutable and scanned on push.
- API, worker, web and admin have separate empty-by-default task roles.
- GitHub deployment uses environment-scoped OIDC, not long-lived AWS keys.
- Secrets Manager resources contain references only. Secret values are
  populated through an approved out-of-band process and never enter Git or
  OpenTofu state.
- VPC flow logs and application logs use KMS encryption and bounded retention.
- MongoDB, Redis, edge ingress and Cloudinary policy are added by C024–C027.

AWS was selected as the initial runtime baseline because ECS Fargate provides
private task networking and per-task IAM without requiring a Kubernetes control
plane. The application and data-provider contracts remain independent of AWS.

## State bootstrap

Run `bootstrap/state` once in each dedicated account with local state held in an
approved restricted workstation or CI bootstrap job. It creates a unique S3
bucket with KMS encryption, versioning, public-access blocking and object lock.
Securely archive or migrate the small bootstrap state after creation.

Object-lock capability is enabled without a bucket-wide default retention:
native S3 backend lock files must be removable after an operation completes.
Version history protects state by default; a reviewed recovery runbook may place
retention on selected state versions without locking `.tflock` objects.

Copy only the non-secret outputs into that environment's untracked
`backend.hcl`:

```powershell
tofu -chdir=infra/bootstrap/state init
tofu -chdir=infra/bootstrap/state plan -out bootstrap.tfplan
tofu -chdir=infra/bootstrap/state apply bootstrap.tfplan

Copy-Item infra/environments/dev/backend.hcl.example `
  infra/environments/dev/backend.hcl
tofu -chdir=infra/environments/dev init `
  -backend-config=backend.hcl
```

The S3 backend uses native lock files. Dev and staging state must never share a
bucket, KMS key or AWS account.

## Safe plan workflow

1. Authenticate with short-lived AWS credentials for exactly one environment.
2. Confirm the caller account matches `account_id`; the provider rejects any
   other account.
3. Copy `terraform.tfvars.example` to the ignored `terraform.tfvars` and replace
   placeholders. Never add credentials or secret values.
4. Run `tofu fmt -check -recursive infra`, `tofu init`, `tofu validate`,
   `tofu test` and `trivy config infra`.
5. Save the binary plan as the ignored `*.tfplan`; review destructive changes,
   public exposure, IAM scope, secret handling and cost before approval.
6. Apply the exact reviewed plan. Never run an unreviewed `auto-approve`.

CI uses mocked providers to plan both environment shapes without cloud
credentials or side effects. A real account plan is still mandatory before
apply.

## First deployment

The base task definitions intentionally use desired count zero. Before raising
the count:

- populate every Secrets Manager reference through a restricted channel;
- push the already-tested artifact to ECR and use its `@sha256` digest;
- provision private MongoDB/Redis paths and required egress rules;
- provision edge-only ingress, TLS and health checks;
- confirm logs, alarms, rollback and deletion protection;
- promote the same application digest from dev to staging.

Destroying an environment does not authorize deleting remote state, retained
secrets, databases or backups. Those require a separate reviewed runbook.
