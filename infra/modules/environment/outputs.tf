output "environment" {
  description = "Environment name used by policy and deployment gates."
  value       = var.environment
}

output "vpc_id" {
  description = "Isolated environment VPC identifier."
  value       = aws_vpc.this.id
}

output "private_subnet_ids" {
  description = "Private subnets used by ECS and future managed data endpoints."
  value       = values(aws_subnet.private)[*].id
}

output "runtime_security_group_id" {
  description = "Security group extended by edge and managed-data modules."
  value       = aws_security_group.runtime.id
}

output "cluster_arn" {
  description = "Environment ECS cluster ARN."
  value       = aws_ecs_cluster.this.arn
}

output "repository_urls" {
  description = "Immutable ECR repositories keyed by runtime."
  value = {
    for name, repository in aws_ecr_repository.runtime :
    name => repository.repository_url
  }
}

output "secret_arns" {
  description = "Secret references only; no secret values are managed in state."
  value = {
    for environment_name, secret_name in local.secret_environment_variables :
    environment_name => aws_secretsmanager_secret.runtime[secret_name].arn
  }
}

output "deployment_role_arn" {
  description = "GitHub OIDC role scoped to this repository and environment."
  value       = aws_iam_role.github_deploy.arn
}
