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
  "status": "OK",
  "entity": "com.mycompany.entity.Product",
  "id": "1",
  "idType": "java.lang.Integer",
  "resolvedId": 1,
  "nearCache": {
    "hitVerified": true,
    "invalidationVerified": true
  }
}
```

Use these fields to verify that the probe uses the same id type as your entity.

If id conversion fails, the response keeps the normal endpoint shape and reports
the conversion problem:

```json
{
  "status": "ERROR",
  "entity": "com.mycompany.entity.Product",
  "id": "abc",
  "idType": "java.lang.Integer",
  "error": "Failed to convert id 'abc' to java.lang.Integer"
}
```

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

The map near-cache diagnostic uses the same disabled-near-cache contract:

```json
{
  "status": "OK",
  "name": "books",
  "mapName": "books",
  "enabled": false,
  "near": {
    "enabled": false,
    "reason": "Near Cache is not enabled"
  }
}
```

If a JCache cache exists but is not backed by Hazelcast `ICache`, the diagnostic
endpoint returns a structured error response instead of failing with HTTP 500:

```json
{
  "status": "ERROR",
  "error": "Cache cannot be unwrapped to Hazelcast ICache: plain-cache",
  "local": {
    "available": false,
    "reason": "not a Hazelcast cache"
  },
  "near": {
    "enabled": false,
    "reason": "Hazelcast ICache is not available"
  }
}
```

For the full diagnostic response contract, see
[observability](observability.md#diagnostic-response-contracts).

## Production Safety

The probe is read-only except for one targeted JPA `Cache.evict(entityClass, id)`
call. Secure the endpoint with Spring Security and avoid calling it in tight
monitoring loops.
