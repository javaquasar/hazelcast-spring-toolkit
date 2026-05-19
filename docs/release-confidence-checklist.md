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

## GitHub Actions Release Flow

The `Release to Maven Central` workflow starts on:

- manual `workflow_dispatch`
- pushed tags matching `v*`

For tag-based releases, the workflow derives the release version from the tag
name. For example, pushing `v0.7.0` releases version `0.7.0`.

The workflow:

- validates release secrets
- derives the short Gradle signing key id from the full signing subkey id
- runs the release integration test matrix
- builds the signed Central aggregate bundle
- verifies generated `.asc` signatures
- uploads the bundle unless it is a manual dry run

## Manual Dry Run

Before pushing a tag, run the workflow manually with:

| Input | Value |
|---|---|
| `version` | `<releaseVersion>` |
| `publishing_type` | `USER_MANAGED` |
| `dry_run` | `true` |

This verifies the release path without uploading to Maven Central.

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
