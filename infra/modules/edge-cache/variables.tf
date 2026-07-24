variable "zone_id" {
  description = "Cloudflare zone identifier. Supply it through the deployment secret store."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[0-9a-f]{32}$", var.zone_id))
    error_message = "zone_id must be a 32-character lowercase hexadecimal Cloudflare zone ID."
  }
}

variable "public_hostname" {
  description = "Canonical public hostname protected by the cache ruleset."
  type        = string

  validation {
    condition = (
      length(var.public_hostname) <= 253 &&
      can(regex(
        "^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$",
        var.public_hostname
      ))
    )
    error_message = "public_hostname must be a lowercase fully qualified hostname."
  }
}
