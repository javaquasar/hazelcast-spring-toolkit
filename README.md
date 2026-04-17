# Hazelcast Toolkit

**Annotation-driven Hazelcast client integration for Spring Boot.**
Register Compact serialization types with `@HzCompact`, wire IMap listeners with `@HzIMapListener`, and activate Hibernate second-level cache with one property — all without writing a single line of `ClientConfig` boilerplate.

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.x-6db33f)](https://spring.io/projects/spring-boot)
[![Hazelcast 5.5](https://img.shields.io/badge/Hazelcast-5.5-ff6600)](https://hazelcast.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Why this library?

**hazelcat-toolkit** is a high-level, annotation-driven toolkit that brings modern Hazelcast 5+ best practices to Spring Boot applications.

While Spring Boot provides basic Hazelcast auto-configuration, it is intentionally minimal and focused only on core `HazelcastInstance` and `Cache` integration. hazelcat-toolkit goes significantly further by eliminating boilerplate for the most common real-world use cases.

### Key Differences from Official Spring Boot Hazelcast Support

| Feature                              | Official Spring Boot                          | hazelcat-toolkit                                      | Benefit |
|--------------------------------------|-----------------------------------------------|-------------------------------------------------------|---------|
| **Hazelcast Instance**               | Basic client/server auto-config               | Smart client with auto-naming, `HazelcastClientConfigCustomizer` | Cleaner, more maintainable configuration |
| **Compact Serialization**            | Not supported                                 | `@HzCompact` + automatic package scanning (zero-config + explicit serializers) | Modern, efficient, cross-language ready |
| **IMap Event Listeners**             | Manual registration                           | `@HzIMapListener` on Spring beans (auto-registered) | Zero-boilerplate event-driven architecture |
| **Hibernate 2nd-Level Cache**        | No dedicated support                          | Full auto-configuration with safe defaults + known issue documentation | Production-ready L2 caching |
| **Configuration Style**              | Properties + XML/YAML files only              | Annotations + properties + type-safe customizers     | Developer-friendly and type-safe |
| **Multi Boot Version Support**       | Single implementation                         | Dedicated modules for Boot 2 / 3 / 4                 | Future-proof |
| **Test Infrastructure**              | None                                          | Shared Testcontainers (3-node cluster + Postgres)    | Ready for integration testing |
| **Metrics & Observability**          | Basic                                         | Optional metrics controller + Near-Cache health Actuator endpoint | Production monitoring ready |

**In short:**  
Spring Boot gives you the foundation.  
**hazelcat-toolkit** gives you the complete, production-grade Hazelcast experience with almost zero boilerplate.

---

## Quick Start (Spring Boot 3)

```yaml
hazelcast:
  client:
    cluster-name: dev
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
implementation 'io.github.javaquasar:toolkit-spring-boot3:0.1.0'
```

**Maven:**
```xml
<dependency>
    <groupId>io.github.javaquasar</groupId>
    <artifactId>toolkit-spring-boot3</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. Configure `application.yml`

Minimal configuration — connects to a local Hazelcast node:

```yaml
spring:
  application:
    name: my-service

hazelcast:
  client:
    cluster-name: dev
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
public class UserProfile { ... }

@Component
@HzIMapListener(map = "users")        // auto-registered on startup
public class UserListener implements EntryAddedListener<String, UserProfile> {
    @Override
    public void entryAdded(EntryEvent<String, UserProfile> event) { ... }
}
```

That is all the code you need. The toolkit bootstraps the `HazelcastInstance`, scans `com.example.app.model` for `@HzCompact` types, and registers the listener bean against the `users` IMap.

### Runnable Example

If you want a minimal end-to-end sample with `@HzCompact`, `@HzIMapListener`,
Hibernate L2, and switchable `JCACHE` / `HAZELCAST_LOCAL` profiles, see
[`example-spring-boot3`](example-spring-boot3/README.md).

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
public class OrderEntry { ... }
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
    public void entryRemoved(EntryEvent<String, Session> event) { ... }
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

#### Local Performance Notes

A local multi-run characterization of `JCACHE` vs `HAZELCAST_LOCAL`, with and
without client near-cache, is documented in [docs/performance.md](docs/performance.md).
Treat those numbers as engineering guidance, not as a universal benchmark.

### Near-Cache Health Check — `/actuator/hazelcast-near-cache`

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
GET /actuator/hazelcast-near-cache
GET /actuator/hazelcast-near-cache?entity=com.mycompany.entity.Product&id=99
```

**Example response:**

```json
{
  "status": "OK",
  "entity": "com.mycompany.entity.User",
  "id": "42",
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

The final Hazelcast instance name is derived from two optional properties:

| Property | Default |
|---|---|
| `hazelcast.toolkit.client.base-name` | falls back to `hazelcast.client.instance-name` |
| `spring.application.name` | (empty) |

When both are set, the name becomes `<base-name>-<sanitized-application-name>`. Application names are lowercased and reduced to `[a-z0-9-]`.

Examples:

| base-name | application-name | Result |
|---|---|---|
| `hz.client` | `my-service` | `hz.client-my-service` |
| `hz.client` | `Billing/API @ EU` | `hz.client-billing-api-eu` |
| `app-hz-client` | _(absent)_ | `app-hz-client` |

---

## Configuration Reference

### `hazelcast.client.*`

| Property | Default | Description |
|---|---|---|
| `instance-name` | `app-hz-client` | Fallback client instance name |
| `cluster-name` | `dev` | Hazelcast cluster name |
| `network.cluster-members` | `[]` | Cluster member addresses (`host:port`) |
| `network.smart-routing` | `true` | Route operations to owner partition member |

### `hazelcast.toolkit.*`

| Property | Default | Description |
|---|---|---|
| `client.base-name` | _(empty)_ | Base name for client naming (takes precedence over `instance-name`) |
| `compact.base-package` | _(empty)_ | Root package to scan for `@HzCompact` classes |
| `metrics.enabled` | `false` | Enable the optional metrics REST controller |
| `hibernate.l2.enabled` | `false` | Activate Hibernate second-level cache support |
| `hibernate.l2.region-factory` | `JCACHE` | RegionFactory type: `JCACHE` \| `HAZELCAST_LOCAL` \| `HAZELCAST` |
| `hibernate.l2.extended-config` | `false` | Apply full property set (region.factory_class, query cache, statistics) |
| `hibernate.l2.use-query-cache` | `false` | Enable Hibernate query result cache (`extended-config` only) |
| `hibernate.l2.use-statistics` | `false` | Enable Hibernate cache statistics (`extended-config` only) |
| `actuator.near-cache-check.enabled` | `false` | Register the `/actuator/hazelcast-near-cache` endpoint |
| `actuator.near-cache-check.entity-class` | _(empty)_ | Fully-qualified JPA entity class used as probe |
| `actuator.near-cache-check.entity-id` | _(empty)_ | Primary-key value of the probe entity (as String) |

---

## Known Limitations

- **JVM-scoped instance name uniqueness**: Hazelcast forbids two `HazelcastInstance` clients with the same name within a single JVM. In test suites that start multiple Spring contexts, every `@SpringBootTest` class must configure a unique `hazelcast.client.instance-name`.

- **Hibernate 5 composite-key issue**: If you use Hibernate 5 and JPA entities with composite keys, Hazelcast's L2 cache key conversion may fail. See [`docs/hibernate-l2-cachekey-converter-issue.md`](docs/hibernate-l2-cachekey-converter-issue.md) for root cause analysis and workarounds.

- **Boot 4 conditional support**: `toolkit-spring-boot4` is functionally equivalent to the Boot 3 module — it includes JCache, Hibernate L2, and Actuator auto-configurations. Each auto-configuration is guarded by `@ConditionalOnClass` against the relevant type (`HibernatePropertiesCustomizer`, `@Endpoint`, `EntityManagerFactory`). In Spring Boot 4.0.0 these types are not yet published, so the extra configurations remain inactive until Boot 4 ships JPA/Actuator support. The module is opt-in: pass `-PenableBoot4=true` to Gradle.

---

## Modules

| Module | Published | Description |
|---|---|---|
| `toolkit-core` | Yes | Public annotations: `@HzCompact`, `@HzIMapListener` |
| `toolkit-scan-api` | Yes | `ClassScanner` interface |
| `toolkit-scan-reflections` | Yes | `org.reflections`-based scanner implementation |
| `toolkit-runtime` | Yes | `HazelcastClientFactory`, `HazelcastClientConfigCustomizer`, properties |
| `toolkit-spring-common` | Yes | `HzListenersAutoRegistrar` — Spring-aware IMap listener wiring |
| `toolkit-metrics-spring` | Yes | Optional `HzToolkitMetricsController` |
| `toolkit-spring-boot2` | Yes | Spring Boot 2 auto-configuration |
| `toolkit-spring-boot3` | Yes | Spring Boot 3 auto-configuration (primary) |
| `toolkit-spring-boot4` | No | Spring Boot 4 auto-configuration (opt-in; full JCache, Hibernate L2, and Actuator parity; enabled when JPA/Actuator land in Boot 4) |
| `toolkit-testcontainers` | No | Shared Hazelcast + Postgres test infrastructure |
| `example-spring-boot3` | No | Runnable sample app with `@HzCompact`, `@HzIMapListener`, and Hibernate L2 profiles |

---

## Build and Release

```bash
# Build and test everything
./gradlew build

# Run a single test class
./gradlew :toolkit-spring-boot3:test --tests io.github.javaquasar.hazelcast.toolkit.boot3.Boot3MapListenerIntegrationTest

# Build with the optional Boot 4 module
./gradlew -PenableBoot4=true :toolkit-spring-boot4:compileJava
```

### Publishing to Maven Central

Publish one module to a local staging repository:

```bash
./gradlew :toolkit-core:publishMavenJavaPublicationToLocalStagingRepository -PreleaseVersion=0.1.0
```

Bundle all published modules for Central Portal upload:

```bash
./gradlew centralBundleAll -PreleaseVersion=0.1.0
```

GPG signing requires `signingKey` and `signingPassword` in `~/.gradle/gradle.properties`. Staging repos and ZIP bundles are written to each module's `build/` directory.
