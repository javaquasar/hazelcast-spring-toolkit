#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const SEMVER_PATTERN = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/;
const PLACEHOLDER_PATTERN = /\b(?:TODO|TBD|FIXME)\b|<releaseVersion>/i;

export const REQUIRED_PROJECT_DOCUMENTS = [
    'README.md',
    'docs/compatibility-matrix.md',
    'docs/release-confidence-checklist.md',
    'docs/release-publishing.md'
];

export const REQUIRED_RELEASE_SECTIONS = ['Compatibility', 'Verification'];

export function validateReleaseNotes(version, content) {
    const errors = [];

    if (!SEMVER_PATTERN.test(version)) {
        errors.push(`release version must use semantic versioning, received: ${version || '<empty>'}`);
        return errors;
    }

    const normalizedContent = content.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
    const lines = normalizedContent.split('\n');
    const firstContentLine = lines.find((line) => line.trim().length > 0);
    const expectedTitle = `# Release Notes: v${version}`;

    if (firstContentLine !== expectedTitle) {
        errors.push(`first non-empty line must be exactly "${expectedTitle}"`);
    }

    if (PLACEHOLDER_PATTERN.test(normalizedContent)) {
        errors.push('release notes contain an unfinished placeholder (TODO, TBD, FIXME, or <releaseVersion>)');
    }

    const sections = new Map();
    let currentSection;

    for (const line of lines) {
        const heading = line.match(/^##\s+(.+?)\s*$/);
        if (heading) {
            currentSection = heading[1];
            if (!sections.has(currentSection)) {
                sections.set(currentSection, []);
            }
        } else if (currentSection) {
            sections.get(currentSection).push(line);
        }
    }

    for (const section of REQUIRED_RELEASE_SECTIONS) {
        if (!sections.has(section)) {
            errors.push(`required section is missing: ## ${section}`);
            continue;
        }

        const hasContent = sections.get(section).some((line) => line.trim().length > 0);
        if (!hasContent) {
            errors.push(`required section has no content: ## ${section}`);
        }
    }

    return errors;
}

function assertTrackedFile(repositoryRoot, relativePath, errors) {
    const absolutePath = resolve(repositoryRoot, ...relativePath.split('/'));
    if (!existsSync(absolutePath)) {
        errors.push(`required documentation file is missing: ${relativePath}`);
        return;
    }

    if (statSync(absolutePath).size === 0) {
        errors.push(`required documentation file is empty: ${relativePath}`);
    }

    try {
        execFileSync('git', ['ls-files', '--error-unmatch', '--', relativePath], {
            cwd: repositoryRoot,
            stdio: 'ignore'
        });
    } catch {
        errors.push(`required documentation file is not committed: ${relativePath}`);
        return;
    }

    try {
        execFileSync('git', ['diff', '--quiet', 'HEAD', '--', relativePath], {
            cwd: repositoryRoot,
            stdio: 'ignore'
        });
    } catch {
        errors.push(`required documentation file has uncommitted changes: ${relativePath}`);
    }
}

export function validateRepositoryReleaseDocumentation(repositoryRoot, version) {
    const errors = [];
    if (!SEMVER_PATTERN.test(version)) {
        return [`release version must use semantic versioning, received: ${version || '<empty>'}`];
    }

    const releaseNotesPath = `docs/releases/v${version}.md`;
    for (const documentPath of [...REQUIRED_PROJECT_DOCUMENTS, releaseNotesPath]) {
        assertTrackedFile(repositoryRoot, documentPath, errors);
    }

    const releaseNotesAbsolutePath = resolve(repositoryRoot, ...releaseNotesPath.split('/'));
    if (existsSync(releaseNotesAbsolutePath)) {
        errors.push(...validateReleaseNotes(version, readFileSync(releaseNotesAbsolutePath, 'utf8')));
    }

    return errors;
}

function main() {
    const version = process.argv[2] ?? '';
    const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
    const errors = validateRepositoryReleaseDocumentation(repositoryRoot, version);

    if (errors.length > 0) {
        console.error('Release documentation validation failed:');
        for (const error of errors) {
            console.error(`- ${error}`);
        }
        process.exitCode = 1;
        return;
    }

    console.log(`Release documentation is ready: docs/releases/v${version}.md`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    main();
}
