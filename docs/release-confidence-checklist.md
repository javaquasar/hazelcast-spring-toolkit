# Release Confidence Checklist

Use this checklist before creating or pushing a release tag. It is intentionally
shorter than the full publishing guide so it can be followed during an ordinary
patch or minor release.

## Before The Tag

1. Confirm the working tree has no tracked changes:

```bash
git status --short
```

Old local drafts and untracked artifacts are acceptable only if they are known
and intentionally excluded from the release commit.

2. Run the full test suite:

```bash
./gradlew test --no-daemon
```

3. Run whitespace validation:

```bash
git diff --check
```

4. Build and sign the aggregate Central bundle:

```bash
./gradlew clean collectCentralAggregateBundle "-PreleaseVersion=<releaseVersion>" --stacktrace
```

5. Confirm the bundle exists:

```text
build/central-bundles/hazelcast-toolkit-<releaseVersion>-central-bundle.zip
```

6. Confirm the release notes exist and match the intended version:

```text
docs/releases/v<releaseVersion>.md
```

Run the same documentation gate used by the release workflow:

```bash
node --test scripts/validate-release-notes.test.mjs
node scripts/validate-release-notes.mjs <releaseVersion>
```

7. Verify generated publication metadata before uploading:

```bash
./gradlew clean publishMavenJavaPublicationToLocalStagingRepository "-PreleaseVersion=<releaseVersion>" --stacktrace
```

Inspect the staged module directories under:

```text
<module>/build/staging-repo/io/github/javaquasar/<artifact>/<releaseVersion>/
```

Each published module should contain:

- `.pom`
- `.module`
- main JAR
- `-sources.jar`
- `-javadoc.jar`
- `.asc` signatures for published artifacts
- checksum files in the final Central bundle

Spot-check generated POM metadata:

- `groupId` is `io.github.javaquasar`
- artifact ids use the `hazelcast-toolkit-*` public naming scheme
- version equals `<releaseVersion>`
- license, SCM, and developer metadata are present
- optional dependencies such as `hazelcast-spring` are not pulled into default
  starter runtime metadata unless explicitly requested by the consuming service

8. Keep release commands version-neutral in docs and notes. Prefer placeholders
   such as `<releaseVersion>` in reusable guides, and put concrete versions only
   in per-version release notes under `docs/releases/`.

## GitHub Actions Release Flow

The `Release to Maven Central` workflow starts on:

- manual `workflow_dispatch`
- pushed tags matching `v*`

For tag-based releases, the workflow derives the release version from the tag
name. For example, pushing `v0.7.0` releases version `0.7.0`.

The workflow:

- validates the versioned release notes and required release documentation before
  requesting access to the `maven-central` environment
- validates release secrets
- derives the short Gradle signing key id from the full signing subkey id
- runs the release integration test matrix
- builds the signed Central aggregate bundle
- verifies generated `.asc` signatures
- uploads the bundle unless it is a manual dry run
- creates or updates the GitHub Release from the versioned release-notes file
  after a successful non-dry-run upload

## Manual Dry Run

Before pushing a tag, run the workflow manually with:

| Input | Value |
|---|---|
| `version` | `<releaseVersion>` |
| `publishing_type` | `USER_MANAGED` |
| `dry_run` | `true` |

This verifies the release path without uploading to Maven Central.

## Tag Commands

Use a concrete version only at the shell boundary:

```bash
git tag -a v<releaseVersion> -m "Release v<releaseVersion>"
git push origin main
git push origin v<releaseVersion>
```

If a tag was pushed before verification completed, delete it locally and remotely:

```bash
git tag -d v<releaseVersion>
git push origin :refs/tags/v<releaseVersion>
```

## After Publishing

After Maven Central shows the version as available, run the published consumer
smoke checks:

```bash
./gradlew :example-spring-boot2:test -PusePublishedToolkit=true "-PtoolkitReleaseVersion=<releaseVersion>"
./gradlew :example-spring-boot3:test -PusePublishedToolkit=true "-PtoolkitReleaseVersion=<releaseVersion>"
```

The same check is available from the `Integration Tests` workflow with the
`published-consumer-smoke` suite.

## Related Docs

- [Release publishing guide](release-publishing.md)
- [Maven Central publishing notes](maven-central-publishing.md)
