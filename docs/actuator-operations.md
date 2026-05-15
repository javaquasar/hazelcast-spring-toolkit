# Actuator Operations

This guide describes how to expose the toolkit's observability surfaces in a
service without turning debugging endpoints into a production API.

## Recommended Production Exposure

For production, start with passive health and metrics:

```properties
management.endpoints.web.exposure.include=health,metrics,info,prometheus
management.endpoint.health.show-details=when_authorized

hazelcast.toolkit.health.enabled=true
hazelcast.toolkit.metrics.enabled=true
```

Use this setup for readiness checks, dashboards, Prometheus scraping, and alerting.

The toolkit registers the health bean as:

```text
hazelcastToolkitHealthIndicator
```

Spring Boot Actuator normally exposes that bean as the health component:

```text
hazelcastToolkit
```

Example health-group setup for Kubernetes-style deployments:

```properties
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.liveness.include=livenessState
management.endpoint.health.group.readiness.include=readinessState,hazelcastToolkit
```

With this setup:

- liveness stays focused on whether the process should be restarted
- readiness includes whether the toolkit-managed Hazelcast client is usable
- `hazelcastToolkit` reports `DOWN` when the client lifecycle is stopped, no
  cluster members are visible, or Hazelcast status inspection fails

## Optional Active Probe

The near-cache actuator probe is useful when operations teams need an active
entity-level check:

```properties
management.endpoints.web.exposure.include=health,metrics,info,prometheus,hazelcastNearCache

hazelcast.toolkit.actuator.near-cache-check.enabled=true
hazelcast.toolkit.actuator.near-cache-check.entity-class=com.mycompany.entity.User
hazelcast.toolkit.actuator.near-cache-check.entity-id=42
```

The endpoint is:

```text
GET /actuator/hazelcastNearCache
```

It performs real JPA loads and one targeted `Cache.evict(entityClass, id)` call.
Secure it and avoid polling it in tight loops.

## Manual Diagnostics

The `/hz-toolkit` controller is a manual debugging surface, separate from Spring
Boot Actuator:

```properties
hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true
```

Expose it only in trusted environments or protect it with application security
rules.

Diagnostic endpoints:

```text
GET /hz-toolkit/hz/objects
GET /hz-toolkit/hz/maps
GET /hz-toolkit/hz/map/near-stats/{mapName}
GET /hz-toolkit/hz/jcache/near-stats/{cacheName}
```

## Which Surface To Use?

| Surface | Endpoint | Purpose | Production posture |
|---|---|---|---|
| Health | `/actuator/health` | Passive readiness of the Hazelcast client | Recommended |
| Micrometer | `/actuator/metrics`, `/actuator/prometheus` | Continuous dashboards and alerts | Recommended |
| Near-cache probe | `/actuator/hazelcastNearCache` | Active entity-level near-cache/L2 verification | Opt-in and secured |
| Diagnostics | `/hz-toolkit/...` | Manual cache/map inspection while debugging | Trusted or secured only |

## Minimal Services-Style Setup

```properties
management.endpoints.web.exposure.include=health,metrics,info,prometheus,hazelcastNearCache
management.endpoint.health.show-details=when_authorized

hazelcast.toolkit.health.enabled=true
hazelcast.toolkit.metrics.enabled=true
hazelcast.toolkit.actuator.near-cache-check.enabled=true
hazelcast.toolkit.actuator.near-cache-check.entity-class=com.starfish.service.common.module.transactional.game.model.GameSystem
hazelcast.toolkit.actuator.near-cache-check.entity-id=1
```

Enable diagnostics temporarily when investigating cache state:

```properties
hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true
```

## Troubleshooting Health

If `hazelcastToolkit` is missing from `/actuator/health`, check:

- `spring-boot-actuator` is on the classpath
- `hazelcast.toolkit.health.enabled=true`
- a `HazelcastInstance` bean exists
- the relevant health group includes `hazelcastToolkit`

If `hazelcastToolkit` is `DOWN`, inspect the details:

| Detail | Meaning |
|---|---|
| `lifecycleRunning=false` | The Hazelcast client lifecycle is stopped. |
| `memberCount=0` | The client is running but cannot see a cluster member. |
| `clusterState` | Hazelcast cluster state as seen by the client. |
| `error` | The health indicator could not inspect Hazelcast status. |

Treat `memberCount=0` as a readiness problem. The service process may be alive,
but it is not ready to use Hazelcast-backed caches safely.
