# Near-Cache Actuator Troubleshooting

The near-cache probe endpoint is:

```text
GET /actuator/hazelcastNearCache
GET /actuator/hazelcastNearCache?entity=com.mycompany.entity.Product&id=99
```

Enable it with:

```properties
management.endpoints.web.exposure.include=health,metrics,info,hazelcastNearCache
hazelcast.toolkit.actuator.near-cache-check.enabled=true
hazelcast.toolkit.actuator.near-cache-check.entity-class=com.mycompany.entity.Product
hazelcast.toolkit.actuator.near-cache-check.entity-id=99
```

## Endpoint Is Missing

Check:

- `spring-boot-actuator` is on the classpath
- JPA is configured and an `EntityManagerFactory` bean exists
- `hazelcast.toolkit.actuator.near-cache-check.enabled=true`
- `management.endpoints.web.exposure.include` contains `hazelcastNearCache`

The endpoint id is camel-case: `hazelcastNearCache`.

## Entity Not Found

Use an id that exists in the database visible to the service instance. The probe
opens fresh persistence contexts and performs real JPA `find(...)` calls.

## Wrong Id Type

The endpoint converts the `id` query parameter using the JPA metamodel id type:

```text
emf.getMetamodel().entity(entityClass).getIdType().getJavaType()
```

Supported simple id types:

- `Integer` / `int`
- `Long` / `long`
- `Short` / `short`
- `Byte` / `byte`
- `String`
- value-object ids with `static valueOf(String)`

Successful responses include:

```json
{
  "id": "1",
  "idType": "java.lang.Integer",
  "resolvedId": 1
}
```

Use these fields to verify that the probe uses the same id type as your entity.

## Composite Ids

Composite ids are intentionally unsupported by this endpoint. The probe accepts a
single `id` query parameter and maps it to one simple id value.

For composite-id entities, use a different cacheable entity as the probe target,
or add a service-local diagnostic endpoint that understands your composite id
shape.

## Hibernate Stats Are Missing

If `hibernateStats` is absent, the probe falls back to timing heuristics.

For precise hit/miss verification, enable:

```properties
hazelcast.toolkit.hibernate.l2.use-statistics=true
```

When using the toolkit's extended L2 configuration, also enable:

```properties
hazelcast.toolkit.hibernate.l2.extended-config=true
```

## Near-Cache Is Not Configured

The actuator probe verifies behavior through Hibernate L2 cache statistics and
JPA cache eviction. For manual Hazelcast object inspection, enable diagnostic
endpoints separately:

```properties
hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true
```

Then inspect:

```text
GET /hz-toolkit/hz/jcache/near-stats/{cacheName}
GET /hz-toolkit/hz/map/near-stats/{mapName}
```

If Near Cache is not enabled for a JCache cache, the diagnostic response reports:

```json
{
  "near": {
    "enabled": false,
    "reason": "Near Cache is not enabled"
  }
}
```

This is a normal diagnostic outcome, not an HTTP failure.

## Production Safety

The probe is read-only except for one targeted JPA `Cache.evict(entityClass, id)`
call. Secure the endpoint with Spring Security and avoid calling it in tight
monitoring loops.
