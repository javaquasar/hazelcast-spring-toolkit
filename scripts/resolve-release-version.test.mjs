import assert from 'node:assert/strict';
import test from 'node:test';

import {
    readProjectVersion,
    resolveReleaseVersion
} from './resolve-release-version.mjs';

test('reads the release version from gradle properties', () => {
    assert.equal(readProjectVersion('group=example\nversion=0.12.0\n'), '0.12.0');
});

test('uses the project version for a manual workflow run', () => {
    assert.equal(
        resolveReleaseVersion('workflow_dispatch', 'release-v0.12.0', '0.12.0'),
        '0.12.0'
    );
});

test('uses a matching tag version for a tag-triggered run', () => {
    assert.equal(resolveReleaseVersion('push', 'v0.12.0', '0.12.0'), '0.12.0');
});

test('rejects a tag that does not match the project version', () => {
    assert.throws(
        () => resolveReleaseVersion('push', 'v0.12.1', '0.12.0'),
        /does not match gradle\.properties version/
    );
});

test('rejects snapshot project versions', () => {
    assert.throws(
        () => resolveReleaseVersion('workflow_dispatch', 'main', '0.13.0-SNAPSHOT'),
        /non-SNAPSHOT semantic version/
    );
});

test('requires exactly one project version property', () => {
    assert.throws(() => readProjectVersion('group=example\n'), /exactly one/);
    assert.throws(() => readProjectVersion('version=0.12.0\nversion=0.12.1\n'), /exactly one/);
});
