# Observability

`hazelcast-toolkit` exposes observability through two separate layers:

1. **Micrometer meters** for production monitoring and dashboards
2. **Spring Boot health** for readiness-style Hazelcast client status
3. **Diagnostic HTTP endpoints** for manual inspection and troubleshooting

Use them differently.

## Production Metrics: Micrometer

Enable Micrometer binders with:

```yaml
hazelcast:
  toolkit:
    metrics:
      enabled: true
```

These binders are intended for Prometheus / Grafana / alerting pipelines.

### Near-Cache Metrics

The toolkit publishes near-cache metrics for both:

- Hazelcast `IMap`
- JCache / `ICache`

Meters:

| Meter name | Type | Meaning |
|---|---|---|
| `hazelcast.toolkit.near.cache.enabled` | gauge | `1` when near-cache stats are available, else `0` |
| `hazelcast.toolkit.near.cache.hits` | function counter | Near-cache hits |
| `hazelcast.toolkit.near.cache.misses` | function counter | Near-cache misses |
| `hazelcast.toolkit.near.cache.invalidations` | function counter | Near-cache invalidations |
| `hazelcast.toolkit.near.cache.evictions` | function counter | Near-cache evictions |
| `hazelcast.toolkit.near.cache.expirations` | function counter | Near-cache expirations |
| `hazelcast.toolkit.near.cache.owned.entries` | gauge | Current owned entry count |
| `hazelcast.toolkit.near.cache.owned.entry.memory.bytes` | gauge | Current memory footprint in bytes |

Tags:

| Tag | Example | Meaning |
|---|---|---|
| `cache` | `test-entity-region` | Map or cache name |
| `kind` | `imap`, `jcache` | Source data structure type |

### Runtime Auto-Registration

The near-cache binder instruments:

- maps and caches already present at startup
- new distributed objects created later at runtime

This means runtime-created maps and JCache caches do not require manual metric
registration.

### Hibernate L2 Metrics

When Hibernate L2 is enabled, the toolkit publishes global Hibernate statistics.

Meters:

| Meter name | Type | Meaning |
|---|---|---|
| `hazelcast.toolkit.hibernate.l2.hit.count` | function counter | L2 cache hits |
| `hazelcast.toolkit.hibernate.l2.miss.count` | function counter | L2 cache misses |
| `hazelcast.toolkit.hibernate.l2.put.count` | function counter | L2 cache puts |
| `hazelcast.toolkit.hibernate.query.cache.hit.count` | function counter | Query cache hits |
| `hazelcast.toolkit.hibernate.query.cache.miss.count` | function counter | Query cache misses |
| `hazelcast.toolkit.hibernate.query.cache.put.count` | function counter | Query cache puts |
| `hazelcast.toolkit.hibernate.statistics.enabled` | gauge | `1` when Hibernate statistics are enabled |

Tags:

| Tag | Example | Meaning |
|---|---|---|
| `regionFactory` | `JCACHE` | Configured Hibernate region-factory mode |

## Health Indicator

Enable the optional Hazelcast toolkit health indicator with:

```yaml
hazelcast:
  toolkit:
    health:
      enabled: true
```

When Spring Boot Actuator health support is on the classpath, this contributes a
`hazelcastToolkitHealthIndicator` bean to `/actuator/health`.

The indicator reports:

- `instanceName`
- `lifecycleRunning`
- `clusterState`
- `memberCount`

Example details:

```json
{
  "status": "UP",
  "details": {
    "instanceName": "gameservice",
    "lifecycleRunning": true,
    "clusterState": "ACTIVE",
    "memberCount": 1
  }
}
```

The indicator is `UP` when the toolkit-managed `HazelcastInstance` lifecycle is
running and at least one cluster member is visible. It is `DOWN` when the client
is not running, no members are visible, or Hazelcast status inspection throws an
exception.

## Diagnostic Endpoint

Enable the lightweight diagnostic controller with:

```yaml
hazelcast:
  toolkit:
    metrics:
      diagnostic-endpoint:
        enabled: true
```

This registers:

- `GET /hz-toolkit/hz/objects`
- `GET /hz-toolkit/hz/maps`
- `GET /hz-toolkit/hz/map/near-stats/{mapName}`
- `GET /hz-toolkit/hz/jcache/near-stats/{cacheName}`

The `/hz-toolkit` controller is intentionally separate from Spring Boot
Actuator. It is opt-in, debugging-oriented, and should be exposed only in
trusted environments or protected by application security rules.

This layer is for:

- local debugging
- manual verification
- troubleshooting cache state

It is **not** the primary production monitoring API.

Diagnostic near-cache responses use a stable top-level shape where possible:

```json
{
  "status": "OK",
  "name": "test-entity-region",
  "local": {
    "available": true
  },
  "near": {
    "enabled": false,
    "reason": "Near Cache is not enabled"
  }
}
```

### Diagnostic Response Contracts

`GET /hz-toolkit/hz/map/near-stats/{mapName}` returns `status`, `name`,
`mapName`, and `near`. For backward compatibility, the near-cache fields are also
mirrored at the top level when statistics are available.

Map with Near Cache enabled:

```json
{
  "status": "OK",
  "name": "books",
  "mapName": "books",
  "enabled": true,
  "hits": 12,
  "misses": 3,
  "invalidations": 1,
  "near": {
    "enabled": true,
    "hits": 12,
    "misses": 3,
    "invalidations": 1
  }
}
```

Map with Near Cache disabled:

```json
{
  "status": "OK",
  "name": "books",
  "mapName": "books",
  "enabled": false,
  "near": {
    "enabled": false
  }
}
```

`GET /hz-toolkit/hz/jcache/near-stats/{cacheName}` returns `status`, `name`,
`cacheName`, `local`, and `near`.

JCache cache with local stats and Near Cache disabled:

```json
{
  "status": "OK",
  "name": "starfish.gamesystem",
  "cacheName": "starfish.gamesystem",
  "local": {
    "available": true,
    "cacheGets": 42,
    "cacheHits": 40,
    "cacheMisses": 2
  },
  "near": {
    "enabled": false,
    "reason": "Near Cache is not enabled"
  }
}
```

JCache cache not found:

```json
{
  "status": "ERROR",
  "name": "missing-cache",
  "cacheName": "missing-cache",
  "error": "Cache not found: missing-cache",
  "local": {
    "available": false
  },
  "near": {
    "enabled": false,
    "reason": "Cache not found"
  }
}
```

The diagnostic controller should return structured JSON for expected cache-state
problems. A disabled Near Cache is represented as `near.enabled=false`, not as an
HTTP 500.

## Recommended Usage

- Use **Micrometer meters** for dashboards, scraping, recording rules, and alerts.
- Use **`/actuator/health`** for passive Hazelcast client readiness.
- Use **`/hz-toolkit` diagnostic endpoints** for manual inspection when debugging a cache issue.
- Use **`/actuator/hazelcastNearCache`** when you want to actively probe whether near-cache invalidation and L2 behavior are working for a specific entity.
- See [actuator operations](actuator-operations.md) for production exposure guidance.
- See [Prometheus and Grafana examples](prometheus-grafana-examples.md) for starter queries and dashboard ideas.
- See [near-cache actuator troubleshooting](near-cache-actuator-troubleshooting.md) for common endpoint errors and expected responses.
- See [compatibility matrix](compatibility-matrix.md) for the supported Boot,
  Hibernate, and Hazelcast lines.

| Surface | Endpoint | Primary use |
|---|---|---|
| Health | `/actuator/health` | Passive readiness of the Hazelcast client |
| Micrometer | `/actuator/metrics`, `/actuator/prometheus` | Continuous monitoring and alerting |
| Active probe | `/actuator/hazelcastNearCache` | Entity-level near-cache/L2 verification |
| Diagnostics | `/hz-toolkit/...` | Manual cache/map inspection |

## JPA And JCache Corner Case

If Hibernate L2 is enabled with `region-factory: JCACHE`, make sure Hibernate is
bound to the toolkit-managed `javax.cache.CacheManager`.

Recommended setup:

```yaml
hazelcast:
  toolkit:
    hibernate:
      l2:
        enabled: true
        extended-config: true
```

Why this matters:

- this keeps Hibernate on the same Hazelcast-backed JCache manager that the toolkit already created
- it avoids Hibernate's fallback path where `JCacheRegionFactory` tries to bootstrap its own default cache manager
- that fallback may create a separate internal Hazelcast client, which is harder to monitor and can be surprising during debugging

If you want to avoid the JCache layer entirely, prefer `region-factory: HAZELCAST_LOCAL`.

## Minimal Example

```yaml
hazelcast:
  toolkit:
    metrics:
      enabled: true
      diagnostic-endpoint:
        enabled: true
    actuator:
      near-cache-check:
        enabled: true
        entity-class: com.mycompany.entity.User
        entity-id: "42"
```

With that setup:

- Micrometer meters are exported by `hazelcast.toolkit.metrics.enabled=true`
- `/actuator/health` includes Hazelcast client readiness details when
  `hazelcast.toolkit.health.enabled=true`
- `/hz-toolkit/...` is available for diagnostics by `hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true`
- `/actuator/hazelcastNearCache` is available for an active near-cache probe
