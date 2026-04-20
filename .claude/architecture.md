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
toolkit-metrics-spring        (Micrometer binders + diagnostic REST endpoints)
   |
toolkit-spring-boot2/3/4      (Spring Boot auto-configuration entry points)
example-spring-boot3          (runnable sample app with compact types, listeners,
   |                           Hibernate L2, near-cache, HTTP flow, Management Center demo)
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
| `HzToolkitMetricsController` | Optional diagnostic REST controller at `/hz-toolkit` for manual inspection of distributed objects, maps, and near-cache state. |
| `HazelcastNearCacheMetricsBinder` | Micrometer binder for near-cache counters and gauges on `IMap` and JCache objects, including runtime-created maps/caches. |
| `HibernateL2MetricsBinder` | Micrometer binder exposing global Hibernate L2 and query-cache statistics via reflective SessionFactory access. |

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

Boot 4 includes the same main auto-configuration areas as Boot 3:

- `HazelcastToolkitAutoConfiguration`
- `HazelcastJCacheAutoConfiguration`
- `HazelcastHibernateL2AutoConfiguration`
- `HazelcastActuatorAutoConfiguration`

Boot 4 test coverage now matches Boot 3 across all major test areas:
auto-config smoke, JCache integration, JPA L2 cache, map listener, actuator near-cache,
L2 cache key-issue (composite keys, converters), and performance characterization.
Boot 4 uses H2 + in-process Hazelcast member (no Testcontainers) whereas Boot 3 uses
PostgreSQL via Testcontainers — this is an intentional infrastructure difference,
not a parity gap.

### example-spring-boot3

The runnable example is now a real integration sample, not a placeholder.
It demonstrates:

- explicit `@HzCompact(serializer = ...)` with nested DTO serialization
- reflective `@HzCompact` on a second map-backed read model
- `@HzIMapListener` event handling on multiple maps
- Hibernate L2 plus near-cache diagnostics
- a real HTTP flow, Actuator near-cache probe, and Management Center demo setup

## Auto-Configuration Registration

| Module | Mechanism | File |
|---|---|---|
| Boot 2 | `spring.factories` | `EnableAutoConfiguration=` entries |
| Boot 3 | `AutoConfiguration.imports` | Three classes listed |
| Boot 4 | `AutoConfiguration.imports` | Four classes listed: main, JCache, Hibernate L2, Actuator |

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
- Shared L2 performance-characterization logic now lives in a common test harness under `toolkit-spring-common/src/testFixtures`.
- Observability is split into Micrometer meters for production monitoring and a separate diagnostic HTTP controller for manual troubleshooting.
- `toolkit-testcontainers` is test-only and not published.
- Boot 4 is opt-in via `-PenableBoot4=true`.

## Test Infrastructure

### Shared Test Fixtures

- `SharedTestApplication` - shared `@SpringBootApplication` and entity scan setup
- `EmbeddedHazelcastTestConfiguration` - in-process Hazelcast member for listener tests
- `ListenerTestConfiguration` - test listener with event counting (`RecordingEntryListener`)
- `HazelcastAutoConfigurationSmokeTestSupport` - shared bean-wiring assertions
- `AbstractHibernateL2PerformanceComparisonSupport` - shared L2 warm-read measurement harness used by all three Boot-specific performance tests
- `AbstractEmbeddedMemberL2CacheTestConfiguration` - shared base for Boot 2 / Boot 4 embedded-member L2 test configurations; concrete subclasses provide cluster name, member address, and lifecycle annotations
- `SharedTestCachedEntity` / `SharedTestCachedEntityRepository` - L2 cache test entities (dual javax+jakarta annotations for Boot 2 / 3 / 4 reuse)
- `l2issue/` package - shared L2 cache key-issue entities (`SharedIssueUser`, composite-key entity variants, `SharedIssueSimpleConvertedEntity`, converter, enum) used by Boot 3 and Boot 4 L2 key-issue tests; Jakarta-only annotations (Boot 2 has its own `LegacyIssue*` copies)
- `SharedL2CacheKeyIssueTestApplication` - shared `@SpringBootApplication` + `@EntityScan` entry point for L2 key-issue tests

### Testcontainers

- `TestcontainersEnvironment` provides static Hazelcast + Postgres containers
- Cluster name is `"core"`
- Hazelcast containers currently rely on port-open, not cluster-ready, waiting
- `registerSpringProperties(registry)` wires datasource and Hazelcast endpoints into tests

### Example App Verification

- `example-spring-boot3` has smoke tests plus a real HTTP integration test
- the example includes `compose.yaml`, `http/demo.http`, and Management Center support for visual verification
