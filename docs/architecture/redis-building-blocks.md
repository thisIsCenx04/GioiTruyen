# Redis building blocks

MySQL remains the source of truth. Redis data must be safe to flush and
rebuild. The application uses string keys and JSON values only; native Java
serialization is not permitted.

## Key contract

Keys use this bounded format:

`gioitruyen:v1:<environment>:<namespace>:<opaque-segment>...`

The supported namespaces are:

| Namespace | Purpose | Redis outage policy |
| --- | --- | --- |
| `cache` | Disposable public/read-model cache | Load from source of truth |
| `rate` | Rate-limit counters | Fail closed or use an explicit bounded policy |
| `session` | Session and token-revocation acceleration | Fail closed |
| `coord` | Short leases and job coordination | Fail closed |

Raw credentials, tokens, personal data and unbounded user input must never be
placed in a key. Cache callers should hash normalized compound queries before
passing an opaque segment to the key factory. Representation changes require a
new key-version or an explicit representation-version segment.

## TTL and failure contract

- Cache TTLs are bounded between 30 seconds and 60 minutes by default.
- A bounded 10 percent jitter prevents many keys expiring simultaneously.
- Redis connect and command timeouts default to 500 ms and 250 ms.
- A cache miss or Redis error always uses the authoritative loader.
- Read, decode and write failures emit low-cardinality metrics without keys or
  values.
- Wallet balances, payment state, canonical permissions and publishing state
  must never exist only in Redis.
- Coordination leases require a TTL plus fencing/version checks at the
  source-of-truth boundary; they never replace a MySQL transaction.

Production TLS, authentication, private networking, memory/eviction and
failover are provisioned by infrastructure commit C025.
