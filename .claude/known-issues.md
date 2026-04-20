# Known Issues

## Scope

This document tracks technical debt, caveats, test pitfalls, Hibernate edge cases,
and resolved history that should not be confused with the active roadmap.

## Current Known Issues / Technical Debt

1. **`toolkit-spring-common` compiles against Spring 6 while Boot 2 runs on Spring 5.**
   Test fixtures inherit the Spring 6 compile dependency, which could become a real
   compatibility issue if fixtures begin to use Spring 6-only APIs.

2. **`HazelcastClientProperties.instanceName` still defaults to `"app-hz-client"`.**
   That is convenient for demos but can be risky when multiple applications share a JVM.

3. **`invalidatesNearCacheWhenAnotherClientUpdatesL2CacheEntry` is still disabled in Boot 2 and Boot 3.**
   The scenario is flaky because of a type mismatch between a raw remote put and the
   Hibernate-serialized `CacheEntry` value format.

## Test And Runtime Caveats

### Multi-Context Naming Rule

Each `@SpringBootTest` class that loads its own Spring context should set a unique
`hazelcast.client.instance-name`. Hazelcast forbids duplicate client instance names
within a single JVM.

### Testcontainers DDL Rule

Tests using the shared Postgres container should use `spring.jpa.hibernate.ddl-auto=create`,
not `create-drop`. The schema drop happens too late during shutdown, after the container
is already gone, which adds noisy connection validation warnings and slows builds down.

### Hibernate 5 + `@EmbeddedId` + L2 Cache

In Boot 2 / Hibernate 5 tests with L2 cache enabled, persisting a new entity with a
serializable scalar `@EmbeddedId` can throw `PersistentObjectException: detached entity passed to persist`.
The practical workaround is to use `entityManager.merge()` instead of `persist()`.

## Hibernate L2 Auto-Configuration Notes

### Philosophy

Hibernate L2 integration is intentionally non-intrusive by default. The toolkit enables
second-level caching, but full wiring happens only when explicitly requested.

### Final Property Set

| Property | Default | Description |
|---|---|---|
| `enabled` | `false` | Master switch |
| `region-factory` | `JCACHE` | `JCACHE` or native Hazelcast region factory mode |
| `extended-config` | `false` | `false` = minimal mode, `true` = fuller property set via `putIfAbsent` |
| `use-query-cache` | `false` | Only relevant with `extended-config=true` |
| `use-statistics` | `false` | Only relevant with `extended-config=true` |

### Behavior Summary

- Minimal mode always applies `hibernate.cache.use_second_level_cache=true`
- All other Hibernate properties are conservative and conflict-aware
- Existing `spring.jpa.properties.*` values should continue to win
- Native region factory modes require `hazelcast-hibernate`

### Region Factory Choice

| Factory | When to use |
|---|---|
| `JCACHE` | Safest and least opinionated default |
| `HAZELCAST_LOCAL` | Recommended native option for most client applications |
| `HAZELCAST` | Use only when stronger cluster-wide consistency justifies the trade-off |

## Resolved In April 2026 (third pass)

- Scanner abstraction boundary restored: `toolkit-runtime` no longer depends on `toolkit-scan-reflections` at all. `CompactClientConfigSupport` and `HazelcastClientFactory` now take `ClassScanner` (from `toolkit-scan-api`). Boot starters wire `ReflectionsClassScanner` as the `ClassScanner` bean and declare `toolkit-scan-reflections` as `implementation` (hidden from consumers). Discovered and fixed a pre-existing bug in `ReflectionsClassScanner`: `ConfigurationBuilder.forPackage()` in Reflections 0.10.2 does not add a package filter; explicit `filterInputsBy(FilterBuilder().includePackage())` call added.
- `toolkit-spring-common` testFixtures now declare `testFixturesImplementation project(':toolkit-scan-reflections')` explicitly (was previously leaking in transitively via `toolkit-runtime`).
- Example app's `ExampleSpringBoot3CompactScanningTest` now has an explicit `testImplementation project(':toolkit-scan-reflections')` dependency instead of relying on transitive exposure.
- `Boot3IntegrationTest` re-enabled and stabilized. The old disabled note was stale: the real hang came from unnecessarily bootstrapping JPA/Hibernate L2 for a test that only validates Hazelcast client connectivity plus the Postgres `DataSource`. The test now uses a lightweight non-web context, disables JPA auto-configuration, keeps `hazelcast.toolkit.hibernate.l2.enabled=false`, and sets a unique `hazelcast.client.instance-name`.
- Public docs now explain the observability split clearly and document the Hibernate L2 / JCACHE corner case: use Micrometer for production monitoring, `/hz-toolkit` for diagnostics, and prefer `extended-config=true` when binding Hibernate L2 to the toolkit-managed JCache path.

## Resolved In April 2026 (second pass)

These items are historical improvements and should not be listed as active backlog:

- Javadoc pass on all core public API classes
- README rewrite with positioning and dependency coordinates
- Dead code removal (`IMapListenerClassesScanner`, `HazelcastJCacheConfig`)
- `HzListenersAutoRegistrar.destroy()` resource leak fix
- Hibernate L2 auto-config redesign with conservative defaults
- Test fixes for the new L2 design and Hibernate 5 merge workaround
- Testcontainers shutdown cleanup via `ddl-auto=create`
- Runnable `example-spring-boot3` with explicit and reflective compact types, listeners, near-cache demo, and Management Center flow
- Shared Hibernate L2 performance harness extracted into `toolkit-spring-common` test fixtures for Boot-specific reuse
- Micrometer near-cache and Hibernate L2 binders with runtime cache/map auto-registration, while keeping `HzToolkitMetricsController` as a diagnostic endpoint
- `CompactRegistration` removed before first public release; `CompactClientConfigSupport` is now the single compact-registration path

## Resolved In April 2026 (first pass — earlier in month)

These items were tracked as active issues and are now resolved:

- Stale Boot 3 local duplicates of `EmbeddedHazelcastTestConfiguration` and `ListenerTestConfiguration` removed; Boot 3 was already using the shared fixtures
- Boot 3 local `l2issue` entity classes moved to shared `toolkit-spring-common` testFixtures (`SharedIssue*` with Jakarta-only annotations) so Boot 3 and Boot 4 share the same entity set
- `AbstractEmbeddedMemberL2CacheTestConfiguration` extracted to shared testFixtures; `Boot2L2CacheTestConfiguration` and `Boot4L2CacheTestConfiguration` are now thin adapters
- Boot 4 test parity with Boot 3 now complete: smoke test, JCache integration, JPA L2 cache, map listener, actuator near-cache, L2 cache key-issue, and performance characterization all present
- `hazelcast-hibernate53` added to Boot 4 test dependencies to enable the `HAZELCAST_LOCAL` scenario in the performance test
