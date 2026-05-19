# Cache Modes Maturity

This guide explains how to choose Spring Cache and Hibernate L2 cache modes when
an application uses Hazelcast Toolkit.

The two settings control different integration layers:

| Setting | Controls | Default |
|---|---|---|
| `hazelcast.toolkit.spring-cache.mode` | Spring `org.springframework.cache.CacheManager` used by `@Cacheable` and application cache utilities | `jcache` |
| `hazelcast.toolkit.hibernate.l2.region-factory` | Hibernate second-level cache region factory | `JCACHE` |

## Recommended Combinations

| Spring Cache mode | Hibernate L2 region factory | Result | Use when |
|---|---|---|---|
| `jcache` | `JCACHE` | Spring Cache and Hibernate L2 both use the toolkit-managed JCache manager | Default, portable setup |
| `native` | `JCACHE` | Spring Cache uses Hazelcast's native Spring cache manager; Hibernate L2 keeps JCache | Legacy services need native Spring Cache semantics while keeping existing Hibernate wiring |
| `native` | `HAZELCAST_LOCAL` | Spring Cache and Hibernate L2 both bypass Spring's JCache cache manager | Advanced native-Hazelcast setup for services that intentionally use Hazelcast Hibernate native factories |
| `none` | `JCACHE` | Application owns Spring Cache; Hibernate L2 still uses toolkit-managed JCache | The service provides its own Spring `CacheManager` bean |
| `none` | disabled | Toolkit does not manage Spring Cache or Hibernate L2 | The service only needs the shared Hazelcast client and low-level APIs |

## Migration Recipe: JCache Spring Cache To Native Spring Cache

Use this when a legacy service previously used Hazelcast's native Spring
`HazelcastCacheManager` and relies on dynamic cache names.

1. Add `hazelcast-spring` to the application classpath.
2. Set:

```properties
hazelcast.toolkit.spring-cache.mode=native
```

3. Keep Hibernate L2 on JCache if the service already uses toolkit-managed
   Hibernate wiring:

```properties
hazelcast.toolkit.hibernate.l2.region-factory=JCACHE
```

4. Verify that Spring sees Hazelcast's native manager:

```java
assertThat(context.getBean(org.springframework.cache.CacheManager.class))
    .isInstanceOf(com.hazelcast.spring.cache.HazelcastCacheManager.class);
```

5. Verify that JCache is still present when Hibernate L2 uses `JCACHE`:

```java
assertThat(context).hasSingleBean(javax.cache.CacheManager.class);
```

## Dynamic Cache Names

The most important behavioral difference is cache-name resolution.

`JCacheCacheManager` returns `null` when a cache name has not been created as a
JCache cache. Hazelcast's native Spring `HazelcastCacheManager` resolves names
lazily through Hazelcast maps, which matches many legacy Hazelcast member-mode
applications.

Choose `native` only when the service needs those native Spring Cache semantics.
The toolkit default remains `jcache` for compatibility and for applications
that intentionally use JCache as the shared cache abstraction.

## User-Provided CacheManager Beans

If the application provides its own Spring `CacheManager`, the toolkit backs off
in every Spring Cache mode. This is intentional: service-local cache ownership
should win over starter auto-configuration.

In `none` mode the toolkit never creates a Spring `CacheManager`, but it can
still create the JCache manager needed by Hibernate L2 when the Hibernate L2
configuration asks for `JCACHE`.
