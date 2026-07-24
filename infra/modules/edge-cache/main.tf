resource "cloudflare_ruleset" "public_catalog_cache" {
  zone_id     = var.zone_id
  name        = "GioiTruyen public catalog cache"
  description = "Fail-closed shared cache policy for anonymous versioned catalog reads"
  kind        = "zone"
  phase       = "http_request_cache_settings"

  rules = [
    {
      ref         = "bypass_credentialed_requests"
      description = "Never cache requests carrying cookies, authorization, or unsafe methods"
      expression  = local.credential_bypass_expression
      action      = "set_cache_settings"
      action_parameters = {
        cache = false
      }
    },
    {
      ref         = "cache_anonymous_public_catalog"
      description = "Cache only anonymous public catalog reads and respect origin TTL"
      expression  = local.public_catalog_expression
      action      = "set_cache_settings"
      action_parameters = {
        cache = true
        edge_ttl = {
          mode = "respect_origin"
        }
        browser_ttl = {
          mode = "respect_origin"
        }
        cache_key = {
          cache_deception_armor      = true
          ignore_query_strings_order = false
        }
        respect_strong_etags = true
      }
    }
  ]
}
