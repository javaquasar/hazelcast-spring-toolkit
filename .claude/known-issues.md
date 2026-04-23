# Known Issues

## Scope

This document tracks technical debt, caveats, test pitfalls, Hibernate edge cases,
and resolved history that should not be confused with the active roadmap.

## Current Known Issues / Technical Debt

- Boot 2 native Hibernate L2 mode is not yet fully decoupled from JCache API presence.
  `hazelcast.toolkit.hibernate.l2.region-factory=HAZELCAST_LOCAL` is documented as the
  path that bypasses JCache, but legacy Boot 2 consumers can still hit
  `NoClassDefFoundError: javax/cache/CacheManager` if always-loaded auto-configuration
  classes expose JCache types during Spring introspection. The detailed engineering note
  is tracked in `docs/tasks/boot2-native-hazelcast-without-jcache.md`.

## Test And Runtime Caveats

### Multi-Context Naming Rule

Each `@SpringBootTest` class that loads its own Spring context should set a unique
`hazelcast.client.instance-name`. Hazlcast forbids duplicate client instance names
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

## Resolved In April 2026 (fourth pass)

- `invalidatesNearCacheWhenAnotherClientEvictsL2CacheEntry` re-enabled in Boot 2 and Boot 3. Root cause: the test was calling `cache.put(cacheKey, "remote-update-marker")` from a second client, storing a raw `String` in a region where Hibernate expects `CacheEntry` objects. Fix: replaced the raw put with `cache.remove(cacheKey)`, which correctly evicts the L2 entry and triggers near-cache invalidation via `invalidateOnChange=true`. Assertions updated: Awaitility waits for `assertNull(hazelcastCache.get(cacheKey))` and the stats check verifies `getInvalidations()` delta or a follow-up miss. The Boot 3 `findEntryContaining()` helper (which relied on `CacheEntry.toString()` containing entity field text) was removed; both tests now take the first available entry key without inspecting the value format.
- Boot 3 `invalidatesNearCacheWhenAnotherClientEvictsL2CacheEntry` had a secondary NPE: `JCache CacheManager.getCache(regionName)` returned null on the remote client. Root cause: JCache `CacheManager.getCache()` is scoped to the manager instance that created the cache — a freshly constructed remote `CacheManager` has an empty local registry and does not look up caches created by other clients in the distributed cluster. Boot 2 was unaffected because the embedded member runs in-JVM and the JVM-level JCache provider state is shared. Fix: replaced the JCache `CachingProvider`/`CacheManager` remote lookup with `remoteClient.getCacheManager().getCache(l2CacheName)` using Hazelcast's native `ICacheManager`. The full ICache name (including URI prefix) is captured from the local side via `hazelcastCache.getName()` and passed to the remote helper, making the lookup a direct distributed-object access by name rather than a JCache-scoped registry check.

## Resolved In April 2026 (third pass)

- Scanner abstraction boundary restored: `toolkit-runtime` no longer depends on `toolkit-scan-reflections` at all. `CompactClientConfigSupport` and `HazelcastClientFactory` now take `ClassScanner` (from `toolkit-scan-api`). Boot starters wire `ReflectionsClassScanner` as the `ClassScanner` bean and declare `toolkit-scan-reflections` as `implementation` (hidden from consumers). Discovered and fixed a pre-existing bug in `ReflectionsClassScanner`: `ConfigurationBuilder.forPackage()` in Reflections 0.10.2 does not add a package filter; explicit `filterInputsBy(FilterBuilder().includePackage())` call added.
- `toolkit-spring-common` testFixtures now declare `testFixturesImplementation project(':toolkit-scan-reflections')` explicitly (was previously leaking in transitively via `toolkit-runtime`).
- Example app's `ExampleSpringBoot3CompactScanningTest` now has an explicit `testImplementation project(':toolkit-scan-reflections')` dependency instead of relying on transitive exposure.
- `Boot3IntegrationTest` re-enabled and stabilized. The old disabled note was stale: the real hang came from unnecessarily bootstrapping JPA/Hibernate L2 for a test that only validates Hazelcast client connectivity plus the Postgres `DataSource`. The test now uses a lightweight non-web context, disables JPA auto-configuration, keeps `hazelcast.toolkit.hibernate.l2.enabled=false`, and sets a unique `hazelcast.client.instance-name`.
- Public docs now explain the observability split clearly and document the Hibernate L2 / JCACHE corner case: use Micrometer for production monitoring, `/hz-toolkit` for diagnostics, and prefer `extended-config=true` when binding Hibernate L2 to the toolkit-managed JCache path.
- Hazelcast client naming is now safer by default. `hazelcast.toolkit.client.base-name` remains the toolkit naming prefix, `hazelcast.client.instance-name` is treated as an explicit direct name when no toolkit base-name is configured, `spring.application.name` becomes the fallback naming source, and the old shared default `app-hz-client` has been removed in favor of a generated unique fallback.
- `toolkit-spring-common` and its `testFixtures` no longer compile against a Spring 6 / Boot 3-only surface. The shared fixture layer now compiles against Spring 5.3 and Boot 2-compatible APIs, while Jakarta annotations remain available separately for Boot 3 / 4 shared entities. This turns the Boot 2 compatibility boundary from a convention into a real compile-time constraint.

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
