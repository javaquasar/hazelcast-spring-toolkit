# Hazelcast Toolkit

**Annotation-driven Hazelcast client integration for Spring Boot.**
Register Compact serialization types with `@HzCompact`, wire IMap listeners with `@HzIMapListener`, and activate Hibernate second-level cache with one property — all without writing a single line of `ClientConfig` boilerplate.

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Spring Boot 2/3/4](https://img.shields.io/badge/Spring%20Boot-2%20%2F%203%20%2F%204-6db33f)](https://spring.io/projects/spring-boot)
[![Hazelcast 5.5](https://img.shields.io/badge/Hazelcast-5.5-ff6600)](https://hazelcast.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Why this library?

**hazelcast-toolkit** is a high-level, annotation-driven toolkit that brings modern Hazelcast 5+ best practices to Spring Boot applications.

While Spring Boot provides basic Hazelcast auto-configuration, it is intentionally minimal and focused only on core `HazelcastInstance` and `Cache` integration. hazelcast-toolkit goes significantly further by eliminating boilerplate for the most common real-world use cases.

### Key Differences from Official Spring Boot Hazelcast Support

| Feature                              | Official Spring Boot                          | hazelcast-toolkit                                      | Benefit |
|--------------------------------------|-----------------------------------------------|-------------------------------------------------------|---------|
| **Hazelcast Instance**               | Basic client/server auto-config               | Smart client with auto-naming, `HazelcastClientConfigCustomizer` | Cleaner, more maintainable configuration |
| **Compact Serialization**            | Not supported                                 | `@HzCompact` + automatic package scanning (zero-config + explicit serializers) | Modern, efficient, cross-language ready |
| **IMap Event Listeners**             | Manual registration                           | `@HzIMapListener` on Spring beans (auto-registered) | Zero-boilerplate event-driven architecture |
| **Hibernate 2nd-Level Cache**        | No dedicated support                          | Full auto-configuration with safe defaults + known issue documentation | Production-ready L2 caching |
| **Configuration Style**              | Properties + XML/YAML files only              | Annotations + properties + type-safe customizers     | Developer-friendly and type-safe |
| **Multi Boot Version Support**       | Single implementation                         | Published starters for Boot 2 / 3 / 4 | Clear release scope |
| **Test Infrastructure**              | None                                          | Shared Testcontainers (single Hazelcast node + Postgres) | Ready for integration testing |
| **Metrics & Observability**          | Basic                                         | Micrometer near-cache + Hibernate L2 meters, diagnostic controller, and Near-Cache health Actuator endpoint | Production monitoring ready |

**In short:**  
Spring Boot gives you the foundation.  
**hazelcast-toolkit** gives you the complete, production-grade Hazelcast experience with almost zero boilerplate.

---

## Quick Start (Spring Boot 3)

```yaml
hazelcast:
  client:
    cluster-name: dev
    enterprise-license-key: ${HZ_ENTERPRISE_LICENSE_KEY:}   # optional, Hazelcast Enterprise only
    network:
      cluster-members:
        - 127.0.0.1:5701
  toolkit:
    compact:
      base-package: com.example.app.model   # @HzCompact classes
    client:
      base-name: hz.client                  # optional smart naming
```

### 1. Add the dependency

**Gradle:**
```groovy
implementation 'io.github.javaquasar:hazelcast-toolkit-spring-boot3:<version>'
```

**Maven:**
```xml
<dependency>
    <groupId>io.github.javaquasar</groupId>
    <artifactId>hazelcast-toolkit-spring-boot3</artifactId>
    <version>${version}</version>
</dependency>
```

Published Maven artifact IDs use the `hazelcast-toolkit-*` prefix, for example:

- `io.github.javaquasar:hazelcast-toolkit-spring-boot2`
- `io.github.javaquasar:hazelcast-toolkit-spring-boot3`
- `io.github.javaquasar:hazelcast-toolkit-spring-boot4`

See [docs/compatibility-matrix.md](docs/compatibility-matrix.md) for the current
Boot, Spring Framework, Hibernate, Hazelcast, and JPA namespace support matrix.

### Companion Dependencies in Real Consumer Apps

For a standard Spring Boot application, the starter is the main entry point:

```groovy
implementation 'io.github.javaquasar:hazelcast-toolkit-spring-boot3:<version>'
```

In stricter enterprise builds, that may not be enough on its own. During real
consumer verification against an external application, a few companion
dependencies had to be declared explicitly because the host project used
centralized dependency management, module-level exclusions, and split
configuration across shared modules.

Add these explicitly when your application layout requires them:

- `com.hazelcast:hazelcast`
  Needed when the host application pins or excludes Hazelcast centrally and does
  not already bring the runtime transitively.
- `io.github.javaquasar:hazelcast-toolkit-scan-reflections`
  Needed when you rely on the Reflections-based scanner outside the simple
  starter-only path, for example in shared library modules or tests that create
  scanner-driven configuration directly.
- `io.micrometer:micrometer-core`
  Needed when toolkit metrics are enabled in an application that does not
  already bring Micrometer through its own starter stack.
- `com.hazelcast:hazelcast-hibernate`
  Needed for native Hibernate L2 modes such as `HAZELCAST_LOCAL` or
  `HAZELCAST`.
- `com.hazelcast:hazelcast-spring`
  Needed only when `hazelcast.toolkit.spring-cache.mode=native` is used. The
  toolkit starters do not expose it transitively for default `jcache` users.
- `javax.cache:cache-api`
  Needed for `region-factory: JCACHE`, and currently still relevant for the
  known Boot 2 native-mode edge case documented in the project notes.

Practical rule:

- start with the Boot starter only
- if your application uses custom dependency management, excludes transitive
  libraries, or moves Hazelcast setup into shared modules, add the companion
  dependencies above explicitly instead of assuming the starter is a single-jar
  distribution

### 2. Configure `application.yml`

Minimal configuration — connects to a local Hazelcast node:

```yaml
spring:
  application:
    name: my-service

hazelcast:
  client:
    cluster-name: dev
    enterprise-license-key: ${HZ_ENTERPRISE_LICENSE_KEY:}   # optional, Hazelcast Enterprise only
    network:
      cluster-members:
        - 127.0.0.1:5701
  toolkit:
    compact:
      base-package: com.example.app.model   # package containing @HzCompact classes
```

### 3. Annotate your types

```java
@HzCompact                            // reflective compact serialization
public class UserProfile {
    private String userId;
}

@Component
@HzIMapListener(map = "users")        // auto-registered on startup
public class UserListener implements EntryAddedListener<String, UserProfile> {
    @Override
    public void entryAdded(EntryEvent<String, UserProfile> event) {
        // handle event
    }
}
```

That is all the code you need. The toolkit bootstraps the `HazelcastInstance`, scans `com.example.app.model` for `@HzCompact` types, and registers the listener bean against the `users` IMap.

### Runnable Example

If you want a minimal end-to-end sample with `@HzCompact`, `@HzIMapListener`,
Hibernate L2, and switchable `JCACHE` / `HAZELCAST_LOCAL` profiles, see
[`example-spring-boot3`](example-spring-boot3/README.md).

For release verification of the Boot 2 starter, the repository also contains a
small consumer smoke module: [`example-spring-boot2`](example-spring-boot2/README.md).

---

## Features

### Compact Serialization — `@HzCompact`

Two registration modes controlled by a single annotation:

**Zero-config (reflective)** — Hazelcast infers the schema from class fields:
```java
@HzCompact
public class OrderEntry {
    private String orderId;
    private BigDecimal amount;
    // getters + setters ...
}
```

**Explicit serializer** — full control over encoding (enums, versioning, cross-language):
```java
@HzCompact(serializer = OrderEntryCompactSerializer.class)
public class OrderEntry {
    private String orderId;
}
```

- Explicit serializers are registered **before** reflective classes (Hazelcast's recommended order).
- The toolkit validates that `serializer.getCompactClass() == annotatedClass` at startup — a mismatch throws `IllegalStateException`.
- Serializer classes must have a public no-args constructor.

### IMap Listeners — `@HzIMapListener`

Annotate any Spring bean that implements `MapListener` or `EntryListener`:

```java
@Component
@HzIMapListener(map = "sessions", localOnly = true)
public class SessionEvictionListener implements EntryRemovedListener<String, Session> {
    @Override
    public void entryRemoved(EntryEvent<String, Session> event) {
        // handle event
    }
}
```

| Attribute | Default | Description |
|---|---|---|
| `map` | (required) | IMap name to listen on |
| `includeValue` | `true` | Include entry value in events |
| `localOnly` | `false` | Listen only to locally-owned partitions |

Listeners are registered after all Spring singletons are initialized (`SmartInitializingSingleton`) and deregistered cleanly on context shutdown (`DisposableBean`). AOP-proxied beans (e.g. `@Transactional`) are handled correctly.

### Hibernate Second-Level Cache

The toolkit is non-intrusive by default. Enable it and then choose how much it configures.

**Minimal mode** (default) — only `hibernate.cache.use_second_level_cache=true` is set.
You configure the rest via `spring.jpa.properties.*`:

```yaml
hazelcast:
  toolkit:
    hibernate:
      l2:
        enabled: true
```

**Full wiring mode** — the toolkit also sets `region.factory_class`, the JCache provider binding,
`use_query_cache`, and `generate_statistics`. Existing `spring.jpa.properties.*` values always win (`putIfAbsent`):

```yaml
hazelcast:
  toolkit:
    hibernate:
      l2:
        enabled: true
        extended-config: true      # apply full property set
        use-query-cache: false     # default false
        use-statistics: false      # default false
```

**Native Hazelcast RegionFactory** (advanced) — bypasses JCache entirely.
`HAZELCAST_LOCAL` is recommended for client applications (near-cache on the client side):

```yaml
hazelcast:
  toolkit:
    hibernate:
      l2:
        enabled: true
        region-factory: HAZELCAST_LOCAL   # or HAZELCAST for full distributed mode
```

Requires `com.hazelcast:hazelcast-hibernate` on the classpath.
With `extended-config=false` (default), only `region.factory_class` and `hazelcast.instance.name` are set in addition to `use_second_level_cache=true`.

| Property | Default | Description |
|---|---|---|
| `enabled` | `false` | Master switch |
| `region-factory` | `JCACHE` | `JCACHE` \| `HAZELCAST_LOCAL` \| `HAZELCAST` |
| `extended-config` | `false` | Apply full property set using `putIfAbsent` |
| `use-query-cache` | `false` | `hibernate.cache.use_query_cache` — `extended-config` only |
| `use-statistics` | `false` | `hibernate.generate_statistics` — `extended-config` only |

#### JPA / JCache Corner Cases

When Hibernate L2 uses `region-factory: JCACHE`, prefer the fully managed path:

```yaml
hazelcast:
  toolkit:
    hibernate:
      l2:
        enabled: true
        extended-config: true
```

This matters because `extended-config: true` binds Hibernate to the toolkit-managed
Hazelcast-backed `javax.cache.CacheManager`.

Without that binding, Hibernate's `JCacheRegionFactory` may fall back to its own
default-cache-manager bootstrap path and create a separate internal Hazelcast client
instead of reusing the toolkit-managed one. In logs or thread dumps this may appear
as an internal client name such as `_hzinstance_jcache_shared`.

Use these rules in real applications:

- If you do not need Hibernate L2, keep `hazelcast.toolkit.hibernate.l2.enabled=false`
- If you want `JCACHE`, prefer `enabled=true` plus `extended-config=true`
- If you want to avoid the JCache layer entirely, use `region-factory: HAZELCAST_LOCAL`
- In tests or multi-context applications, always set a unique `hazelcast.client.instance-name`

#### Native Hibernate L2 and Hazelcast Management Center Storage View

The Hazelcast Management Center storage section depends on which Hibernate L2
`RegionFactory` is used.

When `region-factory: JCACHE` is active, Hibernate stores L2 regions through
Hazelcast JCache. Those regions appear under **Storage -> Caches**.

When `region-factory: HAZELCAST` is active, Hibernate uses Hazelcast's native
`HazelcastCacheRegionFactory`, which stores regions in Hazelcast `IMap`
structures. Those regions appear under **Storage -> Maps**.

For Hazelcast client applications, native Hibernate mode must use the native
client loader so `hazelcast-hibernate` reuses the toolkit-managed client instead
of trying to start or find an embedded member:

```properties
hazelcast.toolkit.hibernate.l2.enabled=true
hazelcast.toolkit.hibernate.l2.extended-config=true
hazelcast.toolkit.hibernate.l2.region-factory=HAZELCAST

spring.jpa.properties.hibernate.cache.hazelcast.use_native_client=true
spring.jpa.properties.hibernate.cache.hazelcast.native_client_instance_name=<toolkit-client-instance-name>
```

Use `HAZELCAST` when the goal is to store Hibernate L2 regions as distributed
Hazelcast maps.

Be careful with `HAZELCAST_LOCAL` in pure client applications. It uses
`HazelcastLocalCacheRegionFactory`, which keeps entries in a local JVM cache and
uses Hazelcast topics for invalidation. It may not create the distributed map
shape expected in Management Center, and with Hazelcast clients it can log
`Client has no local member!` during listener handling.

`hazelcast.toolkit.spring-cache.mode=native` is a separate setting. It affects
Spring's `@Cacheable` / `CacheManager` integration, but it does not change the
Hibernate L2 `RegionFactory`.

#### Local Performance Notes

A local multi-run characterization of `JCACHE` vs `HAZELCAST_LOCAL`, with and
without client near-cache, is documented in [docs/performance.md](docs/performance.md).
Treat those numbers as engineering guidance, not as a universal benchmark.

### Spring Cache Mode

By default, the toolkit keeps its original Spring Cache behavior: it creates a
Hazelcast-backed `javax.cache.CacheManager` and exposes it through Spring's
`JCacheCacheManager`.

```yaml
hazelcast:
  toolkit:
    spring-cache:
      mode: jcache   # default
```

Available modes:

| Mode | Behavior | Use when |
|---|---|---|
| `jcache` | Creates `javax.cache.CacheManager` and Spring `JCacheCacheManager` | Default; good for new apps and JCache / Hibernate-oriented setups |
| `native` | Creates Spring `com.hazelcast.spring.cache.HazelcastCacheManager` around the toolkit-managed `HazelcastInstance` | Legacy apps migrating from Hazelcast member mode that previously used Hazelcast's native Spring cache manager |
| `none` | Does not auto-configure a Spring `CacheManager` | The application wants full control or provides its own cache manager bean |

`native` mode requires `com.hazelcast:hazelcast-spring` on the application
classpath. The toolkit keeps this dependency optional (`compileOnly` in the
starter modules), so applications must add it explicitly when they opt into
`native`. It uses Spring's native Hazelcast cache adapter around the
toolkit-managed `HazelcastInstance`; it does not create an embedded Hazelcast
member. JCache remains available for Hibernate/JPA where the existing JCache
auto-configuration applies, but Spring application caching uses Hazelcast's
native Spring adapter.

Gradle:

```groovy
implementation "com.hazelcast:hazelcast-spring:${hazelcastVersion}"
```

Maven:

```xml
<dependency>
  <groupId>com.hazelcast</groupId>
  <artifactId>hazelcast-spring</artifactId>
  <version>${hazelcast.version}</version>
</dependency>
```

If your application uses Hazelcast Enterprise, declare Enterprise explicitly and
exclude ordinary `com.hazelcast:hazelcast` from `hazelcast-spring` so Enterprise
keeps dependency priority:

```xml
<dependency>
  <groupId>com.hazelcast</groupId>
  <artifactId>hazelcast-enterprise</artifactId>
  <version>${hazelcast.version}</version>
</dependency>

<dependency>
  <groupId>com.hazelcast</groupId>
  <artifactId>hazelcast-spring</artifactId>
  <version>${hazelcast.version}</version>
  <exclusions>
    <exclusion>
      <groupId>com.hazelcast</groupId>
      <artifactId>hazelcast</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

Migration note for Core-style legacy cache semantics:

```properties
hazelcast.toolkit.spring-cache.mode=native
```

Use `native` when a legacy application relied on Hazelcast's native Spring Cache
semantics. `HazelcastCacheManager` resolves cache names lazily as Hazelcast maps,
so `getCache("some.dynamic.cache")` can work even when no JCache cache has been
pre-created. `jcache` is the backward-compatible toolkit default, but it is not
behaviorally equivalent for applications that expect arbitrary dynamic cache
names to resolve automatically.

#### Spring Cache vs Hibernate L2 Cache

Do not confuse Spring Cache mode with Hibernate L2 region-factory mode. They
configure different integration layers and can intentionally use different
backends:

```properties
hazelcast.toolkit.spring-cache.mode=native
hazelcast.toolkit.hibernate.l2.region-factory=JCACHE
```

In this example, application-level Spring caching (`@Cacheable`,
`org.springframework.cache.CacheManager`, and cache utility code) uses
Hazelcast's native Spring `HazelcastCacheManager`, while Hibernate second-level
cache still uses JCache. This is a valid migration setup for legacy applications
that need native Spring Cache semantics while keeping existing JCache-based
Hibernate L2 wiring.

For a fuller decision table and a migration recipe from JCache-backed Spring
Cache to Hazelcast native Spring Cache, see
[docs/cache-modes-maturity.md](docs/cache-modes-maturity.md).

### Observability

The toolkit exposes observability through three complementary surfaces:

1. **Micrometer meters** for production monitoring
2. **`/hz-toolkit/...` diagnostic endpoints** for manual inspection
3. **`/actuator/hazelcastNearCache`** for an active near-cache probe

Enable Micrometer binders:

```yaml
hazelcast:
  toolkit:
    metrics:
      enabled: true
```

Enable the optional Hazelcast health indicator:

```yaml
hazelcast:
  toolkit:
    health:
      enabled: true
```

Enable the diagnostic HTTP controller separately:

```yaml
hazelcast:
  toolkit:
    metrics:
      diagnostic-endpoint:
        enabled: true
```

`/hz-toolkit` is intentionally separate from Spring Boot Actuator. It is an
opt-in debugging surface and should be exposed only in trusted environments or
protected by application security rules.

Documented meter names, tags, and usage guidance live in
[docs/observability.md](docs/observability.md). Troubleshooting guidance for
the active near-cache probe lives in
[docs/near-cache-actuator-troubleshooting.md](docs/near-cache-actuator-troubleshooting.md).
Production exposure guidance lives in
[docs/actuator-operations.md](docs/actuator-operations.md), and starter
Prometheus/Grafana examples live in
[docs/prometheus-grafana-examples.md](docs/prometheus-grafana-examples.md).
Production setup recipes live in
[docs/production-recipes.md](docs/production-recipes.md).
Supported Boot, Hibernate, Hazelcast, and JPA namespace combinations are listed
in [docs/compatibility-matrix.md](docs/compatibility-matrix.md).

### Near-Cache Health Check — `/actuator/hazelcastNearCache`

A lightweight Actuator endpoint that verifies, in production, that the Hazelcast near-cache is functioning correctly for a JPA entity of your choice.

**What it probes:**
1. Loads the entity in a fresh `EntityManager` to populate the L2 / near-cache.
2. Reloads it in a second fresh context — the hit must be served from the near-cache.
3. Evicts it via `JPA Cache.evict()`, which propagates cluster-wide to all near-caches.
4. Reloads once more — this load must reach the database (near-cache is cold).

Hibernate cache statistics are used as the primary hit/miss signal when enabled; sub-millisecond timing serves as a fallback.

**Enable and configure:**

```yaml
hazelcast:
  toolkit:
    actuator:
      near-cache-check:
        enabled: true
        entity-class: com.mycompany.entity.User   # cacheable JPA entity
        entity-id: "42"                           # must exist in the database
    hibernate:
      l2:
        enabled: true
        extended-config: true
        use-statistics: true   # enables precise hit/miss detection
```

**Query parameters** — override defaults per request:

```
GET /actuator/hazelcastNearCache
GET /actuator/hazelcastNearCache?entity=com.mycompany.entity.Product&id=99
```

The `id` value is converted using the JPA metamodel id type. Simple ids such as
`Integer`, `Long`, `Short`, `Byte`, `String`, primitives, and value-object ids
with `static valueOf(String)` are supported. Composite ids are intentionally not
supported by this probe.

**Example response:**

```json
{
  "status": "OK",
  "entity": "com.mycompany.entity.User",
  "id": "42",
  "idType": "java.lang.Long",
  "resolvedId": 42,
  "nearCache": {
    "hitVerified": true,
    "invalidationVerified": true
  },
  "timings": {
    "cachedLoadMs": 0,
    "postEvictionLoadMs": 41
  },
  "hibernateStats": {
    "l2HitsDeltaOnCachedLoad": 1,
    "l2MissesDeltaAfterEviction": 1,
    "l2HitsDeltaAfterEviction": 0
  }
}
```

**Requirements:** `spring-boot-actuator` and `jakarta.persistence` on the classpath. Works with all three region-factory modes (`JCACHE`, `HAZELCAST_LOCAL`, `HAZELCAST`). The endpoint is read-only except for one targeted `Cache.evict()` call on the probe entity — secure it via Spring Security.

---

### Client Customization

Register `HazelcastClientConfigCustomizer` beans to extend the default config (TLS, connection retry, labels, etc.):

```java
@Bean
@Order(10)
public HazelcastClientConfigCustomizer tlsCustomizer() {
    return config -> config.getNetworkConfig()
            .setSSLConfig(new SSLConfig().setEnabled(true));
}
```

All detected customizers are applied in `@Order` sequence before the client is created.

### Client Naming

The final Hazelcast instance name is derived from up to three inputs:

| Property | Default |
|---|---|
| `hazelcast.toolkit.client.base-name` | _(empty)_ |
| `hazelcast.client.instance-name` | _(empty)_ |
| `spring.application.name` | (empty) |

Resolution order:

1. If `hazelcast.toolkit.client.base-name` is set, it acts as the naming prefix.
   If `spring.application.name` is also set, the result is
   `<base-name>-<sanitized-application-name>`.
2. Otherwise, if `hazelcast.client.instance-name` is set, it is used as-is.
3. Otherwise, if `spring.application.name` is set, the sanitized application name is used.
4. Otherwise, the toolkit generates a unique fallback such as `hz-client-<random-suffix>`.

Application names and `base-name` values are lowercased and reduced to `[a-z0-9-]`.

Examples:

| base-name | instance-name | application-name | Result |
|---|---|---|---|
| `hz.client` | `legacy-name` | `my-service` | `hz-client-my-service` |
| `hz.client` | `legacy-name` | `Billing/API @ EU` | `hz-client-billing-api-eu` |
| _(empty)_ | `legacy-name` | `my-service` | `legacy-name` |
| _(empty)_ | _(empty)_ | `my-service` | `my-service` |
| _(empty)_ | _(empty)_ | _(empty)_ | generated unique fallback |

---

## Configuration Reference

### `hazelcast.client.*`

| Property | Default | Description |
|---|---|---|
| `instance-name` | _(empty)_ | Explicit Hazelcast client instance name when toolkit naming policy is not configured |
| `enterprise-license-key` | _(empty)_ | Optional Hazelcast Enterprise license key applied to the client config |
| `cluster-name` | `dev` | Hazelcast cluster name |
| `network.cluster-members` | `[]` | Cluster member addresses (`host:port`) |
| `network.smart-routing` | `true` | Route operations to owner partition member |

### `hazelcast.toolkit.*`

| Property | Default | Description |
|---|---|---|
| `client.base-name` | _(empty)_ | Toolkit naming prefix; when combined with `spring.application.name`, produces `<base-name>-<app-name>` |
| `compact.base-package` | _(empty)_ | Root package to scan for `@HzCompact` classes |
| `spring-cache.mode` | `JCACHE` | Spring Cache manager mode: `JCACHE` \| `NATIVE` \| `NONE` |
| `metrics.enabled` | `false` | Enable Micrometer near-cache and Hibernate L2 binders |
| `metrics.diagnostic-endpoint.enabled` | `false` | Enable the optional `/hz-toolkit/...` diagnostic controller separately from metrics publishing |
| `health.enabled` | `false` | Enable the optional Hazelcast toolkit health indicator for `/actuator/health` |
| `hibernate.l2.enabled` | `false` | Activate Hibernate second-level cache support |
| `hibernate.l2.region-factory` | `JCACHE` | RegionFactory type: `JCACHE` \| `HAZELCAST_LOCAL` \| `HAZELCAST` |
| `hibernate.l2.extended-config` | `false` | Apply full property set (region.factory_class, query cache, statistics) |
| `hibernate.l2.use-query-cache` | `false` | Enable Hibernate query result cache (`extended-config` only) |
| `hibernate.l2.use-statistics` | `false` | Enable Hibernate cache statistics (`extended-config` only) |
| `actuator.near-cache-check.enabled` | `false` | Register the `/actuator/hazelcastNearCache` endpoint |
| `actuator.near-cache-check.entity-class` | _(empty)_ | Fully-qualified JPA entity class used as probe |
| `actuator.near-cache-check.entity-id` | _(empty)_ | Primary-key value of the probe entity; converted through the JPA metamodel id type |

---

## Known Limitations

- **JVM-scoped instance name uniqueness**: Hazelcast forbids two `HazelcastInstance` clients with the same name within a single JVM. In test suites that start multiple Spring contexts, every `@SpringBootTest` class must configure a unique `hazelcast.client.instance-name`.

- **Hibernate 5 composite-key issue**: If you use Hibernate 5 and JPA entities with composite keys, Hazelcast's L2 cache key conversion may fail. See [`docs/hibernate-l2-cachekey-converter-issue.md`](docs/hibernate-l2-cachekey-converter-issue.md) for root cause analysis and workarounds.

- **Boot 4 support**: `toolkit-spring-boot4` is part of the regular build and provides Boot 4 auto-configuration for the shared Hazelcast client, JCache wiring, Hibernate L2 integration, listener registration, metrics binders, and the Near-Cache Actuator endpoint. Some advanced auto-configurations are still guarded by `@ConditionalOnClass` and activate only when the relevant Spring Boot / JPA / Actuator types are present on the application classpath. See [`docs/compatibility-matrix.md`](docs/compatibility-matrix.md) for the current Boot 4 support notes.

---

## Modules

| Module | Published | Description |
|---|---|---|
| `toolkit-core` | Yes | Public annotations: `@HzCompact`, `@HzIMapListener` |
| `toolkit-scan-api` | Yes | `ClassScanner` interface |
| `toolkit-scan-reflections` | Yes | `org.reflections`-based scanner implementation; common companion dependency in shared-module integrations |
| `toolkit-runtime` | Yes | `HazelcastClientFactory`, `HazelcastClientConfigCustomizer`, properties |
| `toolkit-spring-common` | Yes | `HzListenersAutoRegistrar` — Spring-aware IMap listener wiring |
| `toolkit-metrics-spring` | Yes | Optional `HzToolkitMetricsController`; may require explicit `micrometer-core` in stricter consumer builds |
| `toolkit-spring-boot2` | Yes | Spring Boot 2 auto-configuration |
| `toolkit-spring-boot3` | Yes | Spring Boot 3 auto-configuration (primary) |
| `toolkit-spring-boot4` | Yes | Spring Boot 4 auto-configuration starter with shared client, JCache, Hibernate L2, Micrometer, and Actuator support |
| `toolkit-testcontainers` | No | Shared Hazelcast + Postgres test infrastructure |
| `example-spring-boot2` | No | Minimal consumer smoke app for verifying the Boot 2 starter locally or from Maven Central |
| `example-spring-boot3` | No | Runnable sample app with `@HzCompact`, `@HzIMapListener`, and Hibernate L2 profiles |

---

## Build and Release

```bash
# Build and test everything
./gradlew build

# Run a single test class
./gradlew :toolkit-spring-boot3:test --tests io.github.javaquasar.hazelcast.toolkit.boot3.Boot3MapListenerIntegrationTest

# Run the full Boot 4 starter test suite
./gradlew :toolkit-spring-boot4:test

# Verify a published toolkit version from the example consumer app
./gradlew :example-spring-boot2:test -PusePublishedToolkit=true -PtoolkitReleaseVersion=<releaseVersion>
./gradlew :example-spring-boot3:test -PusePublishedToolkit=true -PtoolkitReleaseVersion=<releaseVersion>
```

### Publishing to Maven Central

Publish all Maven publications into local staging repositories:

```bash
./gradlew publishMavenJavaPublicationToLocalStagingRepository -PreleaseVersion=<releaseVersion>
```

Bundle all published modules for Central Portal upload:

```bash
./gradlew centralBundleAll -PreleaseVersion=<releaseVersion>
```

Collect all generated Central Portal bundles into one folder:

```bash
./gradlew collectCentralBundles -PreleaseVersion=<releaseVersion>
```

GPG signing can use either in-memory Gradle properties (`signingKey`, `signingPassword`, optional `signingKeyId`) or the local GPG command (`useGpgCmd=true`). Staging repositories are written under each published module's `build/staging-repo/`, and the final ZIP bundles are collected under `build/central-bundles/`.

For a full release walkthrough, see [docs/release-publishing.md](docs/release-publishing.md) and the private operator notes in [SECRETS.md](SECRETS.md).
