# Release Publishing Guide

This document explains how to build signed release artifacts for `hazelcast-toolkit`
and how to publish them to Maven Central using the generated Central Portal bundles.

## Published Modules

The current public release set includes:

- `toolkit-core`
- `toolkit-runtime`
- `toolkit-scan-api`
- `toolkit-scan-reflections`
- `toolkit-metrics-spring`
- `toolkit-spring-common`
- `toolkit-spring-boot2`
- `toolkit-spring-boot3`
- `toolkit-spring-boot4`

Published artifact IDs use the `hazelcast-toolkit-*` prefix. Module directory names
in this repository stay unchanged; only the published Maven `artifactId` values differ.

## Prerequisites

Before running a signed release build, make sure you have:

- Java 17 available
- working Gradle execution in this repository
- an ASCII-armored PGP private key for signing, or a configured local GPG installation
- the matching signing password
- the intended release version

This build accepts signing and release settings through Gradle properties:

- `releaseVersion`
- `signingKey`
- `signingPassword`
- `signingKeyId` (optional)
- `useGpgCmd=true` (optional alternative to in-memory keys)

You can pass them on the command line, or place them in a local uncommitted
`gradle-local.properties` file.

Example `gradle-local.properties`:

```properties
releaseVersion=0.3.0
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----
...
-----END PGP PRIVATE KEY BLOCK-----
signingPassword=your-passphrase
```

## Step 1: Run The Signed Local Staging Build

This step creates Maven publications for every published module under each module's
local staging repository.

```bash
./gradlew clean publishMavenJavaPublicationToLocalStagingRepository "-PreleaseVersion=0.3.0"
```

What this should produce for each published module:

- main JAR
- `-sources.jar`
- `-javadoc.jar`
- `.pom`
- `.module`
- `.asc` signature files when signing is configured

Artifacts are written under:

```text
<module>/build/staging-repo/io/github/javaquasar/<artifact>/<version>/
```

## Step 2: Verify That Signing Really Happened

If signing is configured correctly, signing tasks should run and `.asc` files should
appear next to the published artifacts.

Things to verify:

- `.asc` files exist for JARs, POMs, and module metadata
- `-sources.jar` exists
- `-javadoc.jar` exists
- generated POM metadata looks correct

If signing tasks are skipped, the build may still succeed locally, but the artifacts
are not ready for Maven Central upload.

## Step 3: Build The Central Portal Bundles

After local staging succeeds, create one uploadable ZIP bundle containing all
published modules:

```bash
./gradlew collectCentralAggregateBundle "-PreleaseVersion=0.3.0"
```

Collected bundle location:

```text
build/central-bundles/hazelcast-toolkit-0.3.0-central-bundle.zip
```

If you still want separate per-module bundles for manual diagnostics, use:

```bash
./gradlew centralBundleAll "-PreleaseVersion=0.3.0"
```

This creates one ZIP per published module under:

```text
<module>/build/central/<module>-<version>-central-bundle.zip
```

Each ZIP should contain Maven repository layout entries, including:

- signed JARs
- signed `-sources.jar`
- signed `-javadoc.jar`
- signed `.pom`
- `.module`
- checksums

## Optional Convenience Step: Collect Per-Module Bundles Into One Folder

If you do not want to browse each module directory manually, use the root helper task:

```bash
./gradlew collectCentralBundles "-PreleaseVersion=0.3.0"
```

Collected bundle location:

```text
build/central-bundles/
```

## Step 4: Run A Final Release Verification Pass

Before uploading anything, run the release-confidence matrix:

```bash
./gradlew :toolkit-runtime:test
```
```bash
./gradlew :toolkit-spring-boot2:test
```
```bash
./gradlew :toolkit-spring-boot3:test
```
```bash
./gradlew :toolkit-spring-boot4:test
```
```bash
./gradlew :example-spring-boot3:test
```

After Maven Central shows the release as available, switch to the post-release
verification branch and run the example against the published artifacts instead
of the local Gradle projects:

```bash
./gradlew :example-spring-boot3:test -PusePublishedToolkit=true -PtoolkitReleaseVersion=0.3.0
```

The example test suite verifies the published Boot 3 starter in the demo
application, including default `jcache` Spring Cache mode, explicit `native`
Spring Cache mode, `none` mode, Hibernate L2 cache behavior, compact scanning,
IMap listeners, and the near-cache actuator endpoint.

The `Release to Maven Central` workflow runs this release-confidence matrix before
it builds and uploads the Central Portal bundle. If these checks fail, no upload
is attempted.

You can also run the same checks outside a release with the `Integration Tests`
workflow. It is manual by default to avoid spending CI minutes on every push.

Available suites:

- `docker`: runs `:toolkit-spring-boot3:test`, including Testcontainers-backed tests.
- `release-confidence`: runs the release-confidence matrix listed above.
- `all-tests`: runs `./gradlew test` for the whole repository.

You should also verify that release notes and public documentation match the code:

- `README.md`
- `docs/observability.md`
- `docs/performance.md`
- `docs/releases/v0.3.0.md`

For v0.3.0 and later, also verify that `com.hazelcast:hazelcast-spring`
remains optional for default `jcache` users and is not exposed through starter
runtime metadata:

```bash
./gradlew :toolkit-spring-boot2:dependencyInsight --configuration runtimeClasspath --dependency hazelcast-spring
./gradlew :toolkit-spring-boot3:dependencyInsight --configuration runtimeClasspath --dependency hazelcast-spring
./gradlew :toolkit-spring-boot4:dependencyInsight --configuration runtimeClasspath --dependency hazelcast-spring
```

Each command should report:

```text
No dependencies matching given input were found
```

## Step 5: Upload The Bundle To Maven Central

This repository can upload the aggregate Central Portal bundle from GitHub Actions.
The recommended first-release mode is `USER_MANAGED`: GitHub uploads the bundle,
Central validates it, and you press Publish manually in the portal.

### GitHub Actions Release Flow

Create these repository secrets:

- `SIGNING_KEY`: ASCII-armored private signing key
- `SIGNING_KEY_ID`: signing key id, optional but recommended
- `SIGNING_PASSWORD`: signing key passphrase
- `CENTRAL_PORTAL_USERNAME`: Central Portal user token username
- `CENTRAL_PORTAL_PASSWORD`: Central Portal user token password

Then either:

1. Run `Release to Maven Central` manually from GitHub Actions and enter `0.3.0`.
2. Push a release tag such as `v0.3.0`.

Tag-triggered releases use `USER_MANAGED`. Manual releases can use either
`USER_MANAGED` or `AUTOMATIC`.

After a `USER_MANAGED` upload succeeds:

1. Open `https://central.sonatype.com/publishing`.
2. Wait for the deployment to reach `VALIDATED`.
3. Publish it manually.

Use `AUTOMATIC` only after at least one `USER_MANAGED` release has validated cleanly.

## Ready-To-Copy Upload Set

Use the aggregate file from:

```text
build/central-bundles/hazelcast-toolkit-0.3.0-central-bundle.zip
```

Expected published modules for `0.3.0`:

- `hazelcast-toolkit-core`
- `hazelcast-toolkit-runtime`
- `hazelcast-toolkit-scan-api`
- `hazelcast-toolkit-scan-reflections`
- `hazelcast-toolkit-metrics-spring`
- `hazelcast-toolkit-spring-common`
- `hazelcast-toolkit-spring-boot2`
- `hazelcast-toolkit-spring-boot3`
- `hazelcast-toolkit-spring-boot4`

### Central Portal Upload Table

| Bundle file | Deployment name | ArtifactId | Description |
|---|---|---|---|
| `hazelcast-toolkit-core-0.3.0-central-bundle.zip` | `hazelcast-toolkit-core-0.3.0` | `hazelcast-toolkit-core` | Core annotations for Hazelcast Compact serialization and IMap listener registration. |
| `hazelcast-toolkit-metrics-spring-0.3.0-central-bundle.zip` | `hazelcast-toolkit-metrics-spring-0.3.0` | `hazelcast-toolkit-metrics-spring` | Micrometer binders and diagnostic metrics support for Hazelcast near-cache and Hibernate L2. |
| `hazelcast-toolkit-runtime-0.3.0-central-bundle.zip` | `hazelcast-toolkit-runtime-0.3.0` | `hazelcast-toolkit-runtime` | Shared runtime support for Hazelcast client creation, naming, compact registration, cache mode settings, and config customization. |
| `hazelcast-toolkit-scan-api-0.3.0-central-bundle.zip` | `hazelcast-toolkit-scan-api-0.3.0` | `hazelcast-toolkit-scan-api` | Scanner abstraction used by Hazelcast Toolkit. |
| `hazelcast-toolkit-scan-reflections-0.3.0-central-bundle.zip` | `hazelcast-toolkit-scan-reflections-0.3.0` | `hazelcast-toolkit-scan-reflections` | Reflections-based scanner implementation for annotated Hazelcast types. |
| `hazelcast-toolkit-spring-boot2-0.3.0-central-bundle.zip` | `hazelcast-toolkit-spring-boot2-0.3.0` | `hazelcast-toolkit-spring-boot2` | Spring Boot 2 starter for Hazelcast client integration, compact types, listeners, Hibernate L2, metrics, and configurable Spring Cache mode. |
| `hazelcast-toolkit-spring-boot3-0.3.0-central-bundle.zip` | `hazelcast-toolkit-spring-boot3-0.3.0` | `hazelcast-toolkit-spring-boot3` | Spring Boot 3 starter for Hazelcast client integration, compact types, listeners, Hibernate L2, metrics, near-cache observability, and configurable Spring Cache mode. |
| `hazelcast-toolkit-spring-boot4-0.3.0-central-bundle.zip` | `hazelcast-toolkit-spring-boot4-0.3.0` | `hazelcast-toolkit-spring-boot4` | Spring Boot 4 starter for Hazelcast client integration, compact types, listeners, Hibernate L2, metrics, Actuator support, and configurable Spring Cache mode. |
| `hazelcast-toolkit-spring-common-0.3.0-central-bundle.zip` | `hazelcast-toolkit-spring-common-0.3.0` | `hazelcast-toolkit-spring-common` | Shared Spring integration components, including listener auto-registration. |

## Recommended Release Commands

For a normal signed release build:

```bash
./gradlew clean publishMavenJavaPublicationToLocalStagingRepository -PreleaseVersion=0.3.0
./gradlew collectCentralBundles -PreleaseVersion=0.3.0
./gradlew :toolkit-runtime:test :toolkit-spring-boot2:test :toolkit-spring-boot3:test :toolkit-spring-boot4:test :example-spring-boot3:test
```

If you prefer to separate packaging and verification:

```bash
./gradlew clean publishMavenJavaPublicationToLocalStagingRepository -PreleaseVersion=0.3.0
./gradlew collectCentralBundles -PreleaseVersion=0.3.0
./gradlew :toolkit-spring-boot2:test
./gradlew :toolkit-spring-boot3:test
./gradlew :toolkit-spring-boot4:test
```

## Release Checklist

Before publishing, confirm all of the following:

- signing keys are loaded
- signing tasks are not skipped
- `.asc` files are present
- `sources` and `javadoc` JARs are present
- generated POM metadata is correct
- `collectCentralBundles` succeeds
- Boot 2, Boot 3, and Boot 4 test suites are green
- release notes match the actual module set

## Notes

- `toolkit-testcontainers` is internal test infrastructure and is not published
- `example-spring-boot3` is a runnable example application, not a published library
- `SECRETS.md` contains the Windows-oriented operator notes for signing key setup

```bash
git tag v0.3.0
```

```bash
git push origin v0.3.0
```
