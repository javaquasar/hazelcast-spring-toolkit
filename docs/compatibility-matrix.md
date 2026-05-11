# Compatibility Matrix

This matrix documents the intended starter compatibility surface for the
`0.3.x` line.

The project builds all starters with Java 17. Consumer applications should run on
Java 17 or newer.

## Starter Lines

| Starter artifact | Spring Boot line | JPA namespace | Hibernate line | Notes |
|---|---|---|---|---|
| `hazelcast-toolkit-spring-boot2` | Spring Boot `2.7.x` | `javax.persistence` | Hibernate `5.6.x` | Primary compatibility target for legacy Services-style applications. |
| `hazelcast-toolkit-spring-boot3` | Spring Boot `3.x` | `jakarta.persistence` | Hibernate `6.x` | Main modern Spring Boot line and runnable example target. |
| `hazelcast-toolkit-spring-boot4` | Spring Boot `4.x` | `jakarta.persistence` | Hibernate `6.x` / Boot-managed | Early Boot 4 starter line; keep extra validation before production adoption. |

## Build-Time Reference Versions

These are the versions currently used by the repository test matrix:

| Component | Version |
|---|---|
| Java toolchain | `17` |
| Hazelcast | `5.5.0` |
| Spring Boot 2 | `2.7.18` |
| Spring Boot 3 | `3.5.7` |
| Spring Boot 4 | `4.0.0` |
| Hibernate 5 test line | `5.6.15.Final` |
| Hibernate 6 Boot 3 example line | `6.6.33.Final` |
| JCache API | `1.1.1` |

## Cache Integration Notes

| Area | Boot 2 | Boot 3 | Boot 4 |
|---|---|---|---|
| JCache API | `javax.cache` | `javax.cache` | `javax.cache` |
| JPA API | `javax.persistence` | `jakarta.persistence` | `jakarta.persistence` |
| Default Spring Cache mode | `jcache` | `jcache` | `jcache` |
| Native Spring Cache mode | Requires `com.hazelcast:hazelcast-spring` | Requires `com.hazelcast:hazelcast-spring` | Requires `com.hazelcast:hazelcast-spring` |
| Near-cache actuator endpoint | `/actuator/hazelcastNearCache` | `/actuator/hazelcastNearCache` | `/actuator/hazelcastNearCache` |
| Diagnostic controller | Opt-in `/hz-toolkit/...`, separate from Actuator | Opt-in `/hz-toolkit/...`, separate from Actuator | Opt-in `/hz-toolkit/...`, separate from Actuator |

## Verification Expectations

Before publishing a `0.3.x` patch release, run:

```bash
./gradlew :toolkit-spring-boot2:test
./gradlew :toolkit-spring-boot3:test
./gradlew :toolkit-spring-boot4:test
./gradlew :example-spring-boot2:test
./gradlew :example-spring-boot3:test
```

After Maven Central shows the version as available, run:

```bash
./gradlew :example-spring-boot2:test -PusePublishedToolkit=true -PtoolkitReleaseVersion=<releaseVersion>
./gradlew :example-spring-boot3:test -PusePublishedToolkit=true -PtoolkitReleaseVersion=<releaseVersion>
```

The GitHub Actions `Integration Tests` workflow exposes the same post-release
check as the `published-consumer-smoke` suite.
