mock_provider "cloudflare" {}

variables {
  zone_id         = "0123456789abcdef0123456789abcdef"
  public_hostname = "www.gioitruyen.example"
}

run "anonymous_catalog_cache_plan" {
  command = plan

  assert {
    condition     = cloudflare_ruleset.public_catalog_cache.phase == "http_request_cache_settings"
    error_message = "Catalog caching must use Cloudflare's cache settings phase."
  }

  assert {
    condition     = length(cloudflare_ruleset.public_catalog_cache.rules) == 2
    error_message = "The ruleset must contain both credential bypass and public allowlist rules."
  }

  assert {
    condition = (
      strcontains(local.credential_bypass_expression, "http.cookie ne \"\"") &&
      strcontains(local.credential_bypass_expression, "authorization") &&
      strcontains(local.credential_bypass_expression, "not in {\"GET\" \"HEAD\"}")
    )
    error_message = "The bypass rule must reject cookies, Authorization, and unsafe methods."
  }

  assert {
    condition = (
      strcontains(local.public_catalog_expression, "http.cookie eq \"\"") &&
      strcontains(local.public_catalog_expression, "authorization") &&
      strcontains(local.public_catalog_expression, "starts_with(http.request.uri.path, \"/stories/\")") &&
      !strcontains(local.public_catalog_expression, "/api/workspace") &&
      !strcontains(local.public_catalog_expression, "/account")
    )
    error_message = "The public rule must be credential-free and exclude private surfaces."
  }
}

run "invalid_hostname_is_rejected" {
  command = plan

  variables {
    public_hostname = "https://INVALID.example/path"
  }

  expect_failures = [var.public_hostname]
}
