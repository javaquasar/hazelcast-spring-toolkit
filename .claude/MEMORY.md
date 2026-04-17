# Start Here

Use this file as the entry point into the repository documentation.
It is intentionally short and navigation-focused.

## Reading Order

Read these files in this order when you need to get productive quickly:

1. [README.md](../README.md) - public project overview, value proposition, modules, quick start, build basics
2. [project-knowledge-base.md](project-knowledge-base.md) - canonical hub for internal project memory
3. [architecture.md](architecture.md) - structure, key classes, configuration model, conventions, test infrastructure
4. [known-issues.md](known-issues.md) - technical debt, caveats, resolved history, Hibernate L2 notes
5. [positioning.md](positioning.md) - value proposition, competitors, differentiators, messaging
6. [release-process.md](release-process.md) - build, publishing, signing, release workflow
7. [analysis-what-to-improve-now.md](analysis-what-to-improve-now.md) - active backlog and next improvement priorities only
8. [CLAUDE.md](../CLAUDE.md) - repository-specific working guidance for AI-assisted changes

## Architecture

Go here when you need to understand structure, module boundaries, runtime flow, or class responsibilities:

- [architecture.md](architecture.md)
- [project-knowledge-base.md](project-knowledge-base.md)
  Use the hub first if you are not sure which focused doc to open

## Release And Process

Go here when you need build, publishing, signing, or release workflow details:

- [README.md](../README.md)
  Sections:
  - `Build and Release`
  - `Publishing to Maven Central`
- [CLAUDE.md](../CLAUDE.md)
  Sections:
  - `Build Commands`
  - `Release / Signing`
- [release-process.md](release-process.md)

## Known Issues

Go here when you need current technical risks, caveats, or historical edge cases:

- [known-issues.md](known-issues.md)
- [README.md](../README.md)
  Section:
  - `Known Limitations`
- [SECRETS.md](../SECRETS.md)
  Only when the task touches local release credentials or signing setup

## Current Roadmap

Go here when you need the active backlog and what should be improved next:

- [analysis-what-to-improve-now.md](analysis-what-to-improve-now.md)
  Focus on:
  - `Top Improvement Priorities Now`
  - `Immediate Action Plan`
- [known-issues.md](known-issues.md)
  Use to confirm whether something is current technical debt or already resolved history

## Fast Rules

- Treat `project-knowledge-base.md` as the canonical memory hub.
- Treat `architecture.md`, `known-issues.md`, `positioning.md`, and `release-process.md` as the canonical detailed docs for their scopes.
- Treat `analysis-what-to-improve-now.md` as the active backlog only.
- If a task is marked resolved in the knowledge base, do not keep it in the current roadmap.
- Update this file whenever the documentation structure changes.
