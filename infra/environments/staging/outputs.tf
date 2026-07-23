output "environment" {
  value = module.environment.environment
}

output "vpc_id" {
  value = module.environment.vpc_id
}

output "private_subnet_ids" {
  value = module.environment.private_subnet_ids
}

output "repository_urls" {
  value = module.environment.repository_urls
}

output "secret_arns" {
  sensitive = true
  value     = module.environment.secret_arns
}

output "deployment_role_arn" {
  value = module.environment.deployment_role_arn
}
