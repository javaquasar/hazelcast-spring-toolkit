# Compatibility Matrix

This matrix documents the intended starter compatibility surface for the
`0.5.x` line. It is a support and verification contract, not a promise that every
nearby dependency patch version has been exhaustively tested.

The project builds all starters with Java 17. Consumer applications should run on
Java 17 or newer.

## Starter Lines

| Starter artifact | Spring Boot line | Spring Framework line | JPA namespace | Hibernate line | Status |
|---|---|---|---|---|---|
| `hazelcast-toolkit-spring-boot2` | `2.7.x` | `5.3.x` | `javax.persistence` | `5.6.x` | Primary legacy target for Services-style applications. |
| `hazelcast-toolkit-spring-boot3` | `3.x` | `6.2.x` | `jakarta.persistence` | `6.6.x` | Main modern starter line and example target. |
| `hazelcast-toolkit-spring-boot4` | `4.0.x` | `6.2.x` | `jakarta.persistence` | `6.6.x` / Boot-managed | Early Boot 4 line; release only after full starter tests are green. |

## Build-Time Reference Versions

These are the versions currently used by the repository test matrix:

| Component | Version |
|---|---|
| Java toolchain | `17` |
| Hazelcast | `5.5.0` |
| Spring Boot 2 | `2.7.18` |
| Spring Boot 3 | `3.5.7` |
| Spring Boot 4 | `4.0.0` |
| Spring Framework for Boot 2 compile/test support | `5.3.31` - `5.3.34` |
| Spring Framework for Boot 3 compile/test support | `6.2.0` |
| Spring Framework for Boot 4 compile/test support | `6.2.12` |
| Hibernate 5 test line | `5.6.15.Final` |
| Hibernate 6 test line | `6.6.33.Final` |
| Hazelcast Hibernate native factory bridge | `com.hazelcast:hazelcast-hibernate53:2.2.1` |
| JCache API | `1.1.1` |
| Micrometer core | `1.12.13` |
| Testcontainers | `1.20.4` |

## Cache Integration Notes

| Area | Boot 2 | Boot 3 | Boot 4 |
|---|---|---|---|
| JCache API | `javax.cache` | `javax.cache` | `javax.cache` |
| JPA API | `javax.persistence` | `jakarta.persistence` | `jakarta.persistence` |
| Default Spring Cache mode | `JCACHE` | `JCACHE` | `JCACHE` |
| Native Spring Cache mode | Requires `com.hazelcast:hazelcast-spring` | Requires `com.hazelcast:hazelcast-spring` | Requires `com.hazelcast:hazelcast-spring` |
| Hibernate L2 default mode | `JCACHE` | `JCACHE` | `JCACHE` |
| Native Hibernate L2 modes | Require `com.hazelcast:hazelcast-hibernate53` compatible with Hazelcast 5 | Require `com.hazelcast:hazelcast-hibernate53` compatible with Hazelcast 5 | Require `com.hazelcast:hazelcast-hibernate53` compatible with Hazelcast 5 |
| Near-cache actuator endpoint | `/actuator/hazelcastNearCache` | `/actuator/hazelcastNearCache` | `/actuator/hazelcastNearCache` |
| Diagnostic controller | Opt-in `/hz-toolkit/...`, separate from Actuator | Opt-in `/hz-toolkit/...`, separate from Actuator | Opt-in `/hz-toolkit/...`, separate from Actuator |
| Health indicator | Opt-in `hazelcast.toolkit.health.enabled=true` | Opt-in `hazelcast.toolkit.health.enabled=true` | Opt-in `hazelcast.toolkit.health.enabled=true` |

## Boot 4 Notes

Boot 4 support is intentionally conservative. The module compiles against Boot 4
artifacts, but a few integration types that were present in Boot 3 are currently
provided as compile-time stubs for source compatibility:

- `HibernatePropertiesCustomizer`
- Actuator endpoint annotations such as `@Endpoint` and `@ReadOperation`

Runtime activation remains guarded by `@ConditionalOnClass`. If those owning
types are absent from a consumer application, the corresponding auto-configuration
backs off instead of failing at startup.

## Verification Expectations

Before publishing a `0.5.x` release, run:

```bash
./gradlew test
```

For targeted starter confidence, run:

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
