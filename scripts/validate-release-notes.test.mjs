import assert from 'node:assert/strict';
import test from 'node:test';

import { validateReleaseNotes } from './validate-release-notes.mjs';

const validNotes = `# Release Notes: v1.2.3

## Changes

- Added release automation.

## Compatibility

- No compatibility changes.

## Verification

- Ran the release-confidence matrix.
`;

test('accepts complete release notes', () => {
    assert.deepEqual(validateReleaseNotes('1.2.3', validNotes), []);
});

test('rejects a title for another version', () => {
    assert.match(validateReleaseNotes('1.2.4', validNotes).join('\n'), /first non-empty line/);
});

test('rejects missing required sections', () => {
    const notes = '# Release Notes: v1.2.3\n\n## Changes\n\n- Added release automation.\n';
    const errors = validateReleaseNotes('1.2.3', notes).join('\n');

    assert.match(errors, /## Compatibility/);
    assert.match(errors, /## Verification/);
});

test('rejects empty required sections', () => {
    const notes = '# Release Notes: v1.2.3\n\n## Compatibility\n\n## Verification\n';
    const errors = validateReleaseNotes('1.2.3', notes).join('\n');

    assert.match(errors, /has no content: ## Compatibility/);
    assert.match(errors, /has no content: ## Verification/);
});

test('rejects unfinished placeholders', () => {
    const notes = validNotes.replace('No compatibility changes.', 'TODO');
    assert.match(validateReleaseNotes('1.2.3', notes).join('\n'), /unfinished placeholder/);
});

test('rejects a non-semantic release version', () => {
    assert.match(validateReleaseNotes('release-next', validNotes).join('\n'), /semantic versioning/);
});
