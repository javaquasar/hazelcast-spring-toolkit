# Spring Boot 2 Consumer Smoke

This module is a minimal consumer application for the Boot 2 starter.

It exists to verify that a released `hazelcast-toolkit-spring-boot2` artifact can
be consumed from a normal Spring Boot 2 application with actuator and toolkit
metrics properties enabled.

Local project verification:

```bash
./gradlew :example-spring-boot2:test
```

Post-release verification against Maven Central:

```bash
./gradlew :example-spring-boot2:test -PusePublishedToolkit=true -PtoolkitReleaseVersion=<releaseVersion>
```

## Legacy Native Spring Cache Profile

The `legacy-native-cache` profile demonstrates the Boot 2 migration shape for
services that previously used Hazelcast's native Spring `HazelcastCacheManager`
instead of Spring's JCache-backed `JCacheCacheManager`:

```yaml
hazelcast:
  toolkit:
    spring-cache:
      mode: native
```

For JPA services that also use Hibernate L2 through JCache, keep the Hibernate
L2 wiring explicit:

```yaml
hazelcast:
  toolkit:
    hibernate:
      l2:
        enabled: true
        region-factory: JCACHE
```

In this setup Spring application caching uses Hazelcast's native Spring cache
adapter, while the toolkit-managed `javax.cache.CacheManager` remains available
for Hibernate L2 / JCache-oriented wiring.
