# Architecture

## Scope

This document covers module boundaries, key runtime classes, auto-configuration
registration, configuration model, conventions, and test infrastructure.

## Module Dependency Graph

```text
toolkit-core                  (@HzCompact, @HzIMapListener annotations only)
   |
toolkit-scan-api              (ClassScanner interface - pluggable scanner abstraction)
   |
toolkit-scan-reflections      (ReflectionsClassScanner impl; also compat scanners)
   |
toolkit-runtime               (HazelcastClientFactory, HazelcastClientNameBuilder,
   |                           CompactClientConfigSupport, HazelcastClientConfigCustomizer,
   |                           HazelcastClientProperties, HzToolkitProperties)
   |
toolkit-spring-common         (HzListenersAutoRegistrar; test-fixtures shared infra)
   |
toolkit-metrics-spring        (HzToolkitMetricsController - optional REST stats)
   |
toolkit-spring-boot2/3/4      (Spring Boot auto-configuration entry points)
toolkit-testcontainers        (shared Hazelcast + Postgres containers - NOT published)
```

All `toolkit-spring-boot*` modules declare `api` on core, runtime,
scan-reflections, metrics-spring, and spring-common, so consumers receive the
main integration pieces transitively.

## Key Classes And Responsibilities

### toolkit-core

| Class | Role |
|---|---|
| `@HzCompact` | Marks a class for Hazelcast compact serialization. `serializer()` defaults to `NoopCompactSerializer`, so absence means zero-config reflective registration. |
| `@HzIMapListener` | Marks a Spring bean for auto-registration onto a named IMap. Attributes: `map`, `includeValue`, `localOnly`. |

### toolkit-scan-api

| Class | Role |
|---|---|
| `ClassScanner` | Abstraction with `findAnnotated(pkg, ann)` and `findSubTypes(pkg, superType)`. Intended to allow scanning strategy replacement. |

### toolkit-scan-reflections

| Class | Role |
|---|---|
| `ReflectionsClassScanner` | `ClassScanner` implementation backed by `org.reflections`. |
| `CompactClassesScanner` | Scans for `@HzCompact`, separates explicit serializers from reflective classes, validates serializer/class matching, returns `CompactScanResult`. |
| `CompactScanResult` | Record holding `Set<Class<?>> compactClasses` and `Set<CompactSerializer<?>> serializers`. |

### toolkit-runtime

| Class | Role |
|---|---|
| `HazelcastClientFactory` | Creates `ClientConfig` and `HazelcastInstance`, applying compact registrations and ordered customizers. |
| `HazelcastClientNameBuilder` | Builds the final instance name from `baseName` plus `applicationName`, with sanitization. |
| `CompactClientConfigSupport` | Applies explicit serializers first, then reflective compact classes. |
| `CompactRegistration` | Older utility that only handles reflective compact registration and is now partially superseded. |
| `HazelcastClientConfigCustomizer` | Extension point for client config tuning, typically used as ordered Spring beans. |
| `HazelcastClientProperties` | `@ConfigurationProperties("hazelcast.client")` for client name, cluster name, and network settings. |
| `HzToolkitProperties` | `@ConfigurationProperties("hazelcast.toolkit")` for compact scan, metrics, client naming, and Hibernate L2 features. |

### toolkit-spring-common

| Class | Role |
|---|---|
| `HzListenersAutoRegistrar` | Finds `@HzIMapListener` beans, resolves proxies correctly, registers listeners on startup, deregisters on shutdown. |

### toolkit-metrics-spring

| Class | Role |
|---|---|
| `HzToolkitMetricsController` | Optional REST controller at `/hz-toolkit` exposing object, map, and near-cache metrics endpoints. |

### toolkit-spring-boot3

| Class | Role |
|---|---|
| `HazelcastToolkitAutoConfiguration` | Main auto-configuration: properties, scanners, factory, instance, listener registrar, optional metrics. |
| `HazelcastJCacheAutoConfiguration` | JCache wiring bound to the toolkit-managed `HazelcastInstance`. |
| `HazelcastHibernateL2AutoConfiguration` | Hibernate L2 integration, conservative by default. |

### toolkit-spring-boot2

Boot 2 mirrors Boot 3 conceptually but uses the Boot 2 registration style
(`spring.factories`, `@AutoConfigureAfter`, `@Configuration(proxyBeanMethods=false)`).

### toolkit-spring-boot4

Boot 4 is currently incomplete. Only the main Hazelcast toolkit auto-configuration
is registered; JCache and Hibernate L2 parity are still pending.

## Auto-Configuration Registration

| Module | Mechanism | File |
|---|---|---|
| Boot 2 | `spring.factories` | `EnableAutoConfiguration=` entries |
| Boot 3 | `AutoConfiguration.imports` | Three classes listed |
| Boot 4 | `AutoConfiguration.imports` | Only main class listed |

## Configuration Model

```yaml
hazelcast:
  client:
    instance-name: app-hz-client
    cluster-name: dev
    network:
      cluster-members: [127.0.0.1:5701]
      smart-routing: true
  toolkit:
    client:
      base-name: hz.client
    compact:
      base-package: com.example.hz
    metrics:
      enabled: false
    hibernate:
      l2:
        enabled: false
        region-factory: JCACHE
        extended-config: false
        use-query-cache: false
        use-statistics: false
```

Client name derivation is `<base-name sanitized>-<app-name sanitized>`. If neither
name source is present, the fallback remains `app-hz-client`.

## Conventions

- All auto-configured beans use `@ConditionalOnMissingBean` to preserve overrideability.
- Property binding is split between `hazelcast.client.*` and `hazelcast.toolkit.*`.
- Explicit `CompactSerializer` instances are registered before reflective compact classes.
- `HazelcastClientConfigCustomizer` beans are applied in Spring `@Order`.
- Shared test resources live in `toolkit-spring-common/src/testFixtures`.
- `toolkit-testcontainers` is test-only and not published.
- Boot 4 is opt-in via `-PenableBoot4=true`.

## Test Infrastructure

### Shared Test Fixtures

- `SharedTestApplication` - shared `@SpringBootApplication` and entity scan setup
- `EmbeddedHazelcastTestConfiguration` - in-process Hazelcast member for tests
- `ListenerTestConfiguration` - test listener with event counting
- `HazelcastAutoConfigurationSmokeTestSupport` - shared bean-wiring assertions
- L2 test entities and repositories for cache scenarios

### Testcontainers

- `TestcontainersEnvironment` provides static Hazelcast + Postgres containers
- Cluster name is `"core"`
- Hazelcast containers currently rely on port-open, not cluster-ready, waiting
- `registerSpringProperties(registry)` wires datasource and Hazelcast endpoints into tests
