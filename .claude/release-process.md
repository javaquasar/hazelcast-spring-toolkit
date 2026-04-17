# Release Process

## Build Commands

```bash
# Build everything
./gradlew build

# Run all tests for a module
./gradlew :toolkit-spring-boot3:test

# Run a single test class
./gradlew :toolkit-runtime:test --tests io.github.javaquasar.hazelcast.toolkit.hazelcast.CompactRegistrationTest
./gradlew :toolkit-spring-boot3:test --tests io.github.javaquasar.hazelcast.toolkit.springboot3.config.HazelcastClientFactoryTest

# Build with optional Boot 4 module
./gradlew -PenableBoot4=true :toolkit-spring-boot4:compileJava
```

Java toolchain: Java 17, `options.release = 17`. Tests use JUnit Platform.

## Publishing

```bash
# Publish one module to a local staging repository
./gradlew :toolkit-core:publishMavenJavaPublicationToLocalStagingRepository -PreleaseVersion=0.1.0

# Bundle all published modules for Maven Central upload
./gradlew centralBundleAll -PreleaseVersion=0.1.0
```

Published modules are defined in the root `build.gradle` `publishedModules` list.
Boot 4 and `toolkit-testcontainers` are currently excluded.

## Signing

GPG signing requires:

- `signingKey`
- `signingPassword`

These should be available in `~/.gradle/gradle.properties`.

Staging repositories and ZIP bundles are written to module `build/` directories.

## Release Notes

- Boot 4 remains opt-in via `-PenableBoot4=true`
- Native Hazelcast Hibernate region factory modes require `hazelcast-hibernate`
- Hibernate L2 defaults are intentionally conservative; `extended-config=true` is required for fuller auto-wiring behavior

## Process Guidance

- Update user-facing docs when behavior changes
- Keep internal `.claude` docs in sync with README and current code behavior
- Record resolved historical improvements in `known-issues.md`, not in the active roadmap
- Keep `analysis-what-to-improve-now.md` focused on remaining backlog only
