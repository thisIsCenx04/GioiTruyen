# Public edge cache

This module owns the single zone-level `http_request_cache_settings` ruleset
for the public catalog hostname.

- Only anonymous `GET` and `HEAD` requests for `/`, `/home`, `/search*`, and
  `/stories*` are eligible.
- Any cookie, `Authorization` header, or unsafe method bypasses shared cache.
- Origin `Cache-Control`, strong ETags, and the complete query string (including
  Next.js `_rsc`) are preserved.
- `zone_id` must be injected by the deployment secret store; it is not committed.

This module does not create DNS, TLS, WAF, or origin-lockdown resources. Those
remain part of the prerequisite `INF-002` baseline.
