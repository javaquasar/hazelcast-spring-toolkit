# Prometheus And Grafana Examples

These examples assume Micrometer Prometheus export is enabled by the application
and toolkit metrics are active:

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
hazelcast.toolkit.metrics.enabled=true
```

## Prometheus Queries

Near-cache hit ratio by cache:

```promql
sum by (cache, kind) (rate(hazelcast_toolkit_near_cache_hits_total[5m]))
/
(
  sum by (cache, kind) (rate(hazelcast_toolkit_near_cache_hits_total[5m]))
  +
  sum by (cache, kind) (rate(hazelcast_toolkit_near_cache_misses_total[5m]))
)
```

Near-cache misses by cache:

```promql
sum by (cache, kind) (rate(hazelcast_toolkit_near_cache_misses_total[5m]))
```

Near-cache invalidations by cache:

```promql
sum by (cache, kind) (rate(hazelcast_toolkit_near_cache_invalidations_total[5m]))
```

Near-cache enabled gauge:

```promql
hazelcast_toolkit_near_cache_enabled
```

Hibernate L2 hit ratio:

```promql
sum by (regionFactory) (rate(hazelcast_toolkit_hibernate_l2_hit_count_total[5m]))
/
(
  sum by (regionFactory) (rate(hazelcast_toolkit_hibernate_l2_hit_count_total[5m]))
  +
  sum by (regionFactory) (rate(hazelcast_toolkit_hibernate_l2_miss_count_total[5m]))
)
```

Hibernate statistics enabled:

```promql
hazelcast_toolkit_hibernate_statistics_enabled
```

## Starter Dashboard Draft

| Panel | Query | Notes |
|---|---|---|
| Hazelcast client health | Actuator health scrape or blackbox check | Alert when `/actuator/health` reports DOWN. |
| Near-cache enabled | `hazelcast_toolkit_near_cache_enabled` | Split by `cache` and `kind`. |
| Near-cache hit ratio | Hit-ratio query above | Alert on sustained low ratio for expected hot caches. |
| Near-cache misses | Misses query above | Useful during deploys and cache invalidation events. |
| Near-cache invalidations | Invalidations query above | Watch for unexpected spikes. |
| Hibernate L2 hit ratio | Hibernate hit-ratio query above | Requires Hibernate statistics. |
| Hibernate statistics enabled | `hazelcast_toolkit_hibernate_statistics_enabled` | Should be `1` when relying on Hibernate L2 metrics. |

## Alerting Ideas

Suggested starting points:

- Hazelcast health is `DOWN` for more than 2 minutes.
- `hazelcast_toolkit_near_cache_enabled == 0` for caches expected to use Near Cache.
- Near-cache hit ratio stays below an application-specific threshold for more
  than 10 minutes.
- Hibernate statistics are disabled while L2 metrics dashboards are expected.

Tune all thresholds per service. Cache behavior varies heavily by traffic shape,
entity size, invalidation rate, and deployment pattern.
