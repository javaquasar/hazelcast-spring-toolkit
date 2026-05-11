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
