output "ruleset_id" {
  description = "Identifier of the zone cache ruleset."
  value       = cloudflare_ruleset.public_catalog_cache.id
}

output "credential_bypass_expression" {
  description = "Auditable expression that prevents credentialed shared caching."
  value       = local.credential_bypass_expression
}
