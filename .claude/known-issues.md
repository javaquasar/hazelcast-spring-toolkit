# Known Issues

## Scope

This document tracks technical debt, caveats, test pitfalls, Hibernate edge cases,
and resolved history that should not be confused with the active roadmap.

## Current Known Issues / Technical Debt

1. **`CompactRegistration` in `toolkit-runtime` is incomplete / redundant.**
   It handles only reflective compact classes and does not cover explicit serializers.
   `CompactClientConfigSupport` is the full implementation used by the runtime.

2. **`toolkit-runtime` exposes `toolkit-scan-reflections` via `api`.**
   That leaks a concrete scanner implementation into the public module boundary,
   which weakens the intended `ClassScanner` abstraction.

3. **`toolkit-spring-common` compiles against Spring 6 while Boot 2 runs on Spring 5.**
   Test fixtures inherit the Spring 6 compile dependency, which could become a real
   compatibility issue if fixtures begin to use Spring 6-only APIs.

4. **`HazelcastClientProperties.instanceName` still defaults to `"app-hz-client"`.**
   That is convenient for demos but can be risky when multiple applications share a JVM.

5. **`Boot3IntegrationTest` remains `@Disabled` with a stale reason.**
   The original hang was related to duplicate instance naming; the test now needs a
   proper unique instance-name setup and revalidation.

6. **`invalidatesNearCacheWhenAnotherClientUpdatesL2CacheEntry` is still disabled in Boot 2 and Boot 3.**
   The scenario is flaky because of a type mismatch between a raw remote put and the
   Hibernate-serialized `CacheEntry` value format.

7. **Some Boot 3 local test classes appear duplicated relative to shared test fixtures.**
   `EmbeddedHazelcastTestConfiguration` and `ListenerTestConfiguration` exist both in
   shared fixtures and in local Boot 3 test sources; the local copies look stale.

8. **`toolkit-spring-boot4` is still incomplete.**
   JCache and Hibernate L2 auto-configuration are not yet registered there.

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

## Resolved In April 2026

These items are historical improvements and should not be listed as active backlog:

- Javadoc pass on all core public API classes
- README rewrite with positioning and dependency coordinates
- Dead code removal (`IMapListenerClassesScanner`, `HazelcastJCacheConfig`)
- `HzListenersAutoRegistrar.destroy()` resource leak fix
- Hibernate L2 auto-config redesign with conservative defaults
- Test fixes for the new L2 design and Hibernate 5 merge workaround
- Testcontainers shutdown cleanup via `ddl-auto=create`
