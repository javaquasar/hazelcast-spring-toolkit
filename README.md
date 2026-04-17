# Hazelcast Toolkit

**Annotation-driven Hazelcast client integration for Spring Boot.**
Register Compact serialization types with `@HzCompact`, wire IMap listeners with `@HzIMapListener`, and activate Hibernate second-level cache with one property — all without writing a single line of `ClientConfig` boilerplate.

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.x-6db33f)](https://spring.io/projects/spring-boot)
[![Hazelcast 5.5](https://img.shields.io/badge/Hazelcast-5.5-ff6600)](https://hazelcast.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Why this library?

The official Spring Boot Hazelcast auto-configuration creates an embedded **server** node, not a client — unsuitable for applications that connect to an external cluster. Configuring a Hazelcast **client** with Compact serialization, typed IMap listeners, and Hibernate L2 cache wiring requires scattered boilerplate across `@Configuration` classes. This library replaces all of that with annotations and one `application.yml` block.

| Feature | Spring Boot default | hazelcast-toolkit |
|---|---|---|
| Client vs server | Embedded server | Client only |
| Compact type registration | Manual `ClientConfig` | `@HzCompact` + package scan |
| IMap listener wiring | Manual `addEntryListener()` | `@HzIMapListener` on bean |
| Hibernate L2 cache | Not provided | `hazelcast.toolkit.hibernate.l2.enabled=true` |
| Instance customization | N/A | `HazelcastClientConfigCustomizer` beans |

---

## Quick Start

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
        extended-config: true          # apply full property set
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

---

## Known Limitations

- **JVM-scoped instance name uniqueness**: Hazelcast forbids two `HazelcastInstance` clients with the same name within a single JVM. In test suites that start multiple Spring contexts, every `@SpringBootTest` class must configure a unique `hazelcast.client.instance-name`.

- **Hibernate 5 composite-key issue**: If you use Hibernate 5 and JPA entities with composite keys, Hazelcast's L2 cache key conversion may fail. See [`docs/hibernate-l2-cachekey-converter-issue.md`](docs/hibernate-l2-cachekey-converter-issue.md) for root cause analysis and workarounds.

- **Boot 4 partial support**: `toolkit-spring-boot4` provides the core `HazelcastInstance` bean and listener registration. JCache and Hibernate L2 auto-configuration parity with Boot 3 is planned but not yet complete. The Boot 4 module is opt-in: pass `-PenableBoot4=true` to Gradle.

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
| `toolkit-spring-boot4` | No | Spring Boot 4 auto-configuration (opt-in, in progress) |
| `toolkit-testcontainers` | No | Shared Hazelcast + Postgres test infrastructure |

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
