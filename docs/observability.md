# Observability

`hazelcast-toolkit` exposes observability through two separate layers:

1. **Micrometer meters** for production monitoring and dashboards
2. **Diagnostic HTTP endpoints** for manual inspection and troubleshooting

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

This layer is for:

- local debugging
- manual verification
- troubleshooting cache state

It is **not** the primary production monitoring API.

## Recommended Usage

- Use **Micrometer meters** for dashboards, scraping, recording rules, and alerts.
- Use **`/hz-toolkit` diagnostic endpoints** for manual inspection when debugging a cache issue.
- Use **`/actuator/hazelcast-near-cache`** when you want to actively probe whether near-cache invalidation and L2 behavior are working for a specific entity.

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

- Micrometer meters are exported
- `/hz-toolkit/...` is available for diagnostics
- `/actuator/hazelcast-near-cache` is available for an active near-cache probe
