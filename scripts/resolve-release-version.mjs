#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { SEMVER_PATTERN } from './validate-release-notes.mjs';

export function readProjectVersion(content) {
    const matches = [...content.replace(/^\uFEFF/, '').matchAll(/^\s*version\s*=\s*(.*?)\s*$/gm)];
    if (matches.length !== 1 || !matches[0][1]) {
        throw new Error('gradle.properties must contain exactly one non-empty version property');
    }
    return matches[0][1];
}

export function resolveReleaseVersion(eventName, refName, projectVersion) {
    validateVersion(projectVersion, 'Project version');

    if (eventName === 'workflow_dispatch') {
        return projectVersion;
    }
    if (eventName !== 'push' || !refName.startsWith('v')) {
        throw new Error(`Unsupported release event or ref: ${eventName} ${refName}`);
    }

    const tagVersion = refName.slice(1);
    validateVersion(tagVersion, 'Tag version');
    if (tagVersion !== projectVersion) {
        throw new Error(
            `Tag version ${tagVersion} does not match gradle.properties version ${projectVersion}`
        );
    }
    return tagVersion;
}

function validateVersion(version, label) {
    if (!SEMVER_PATTERN.test(version) || version.toUpperCase().includes('-SNAPSHOT')) {
        throw new Error(`${label} must be a non-SNAPSHOT semantic version, received: ${version || '<empty>'}`);
    }
}

function main() {
    const eventName = process.argv[2] ?? '';
    const refName = process.argv[3] ?? '';
    const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
    const projectVersion = readProjectVersion(
        readFileSync(resolve(repositoryRoot, 'gradle.properties'), 'utf8')
    );
    console.log(resolveReleaseVersion(eventName, refName, projectVersion));
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    try {
        main();
    } catch (error) {
        console.error(error.message);
        process.exitCode = 1;
    }
}
