---
name: hazelcast-toolkit project knowledge base
description: Canonical navigation hub for architecture, known issues, positioning, and release process documentation
type: project
---

# hazelcast-toolkit - Project Knowledge Base

This file is the canonical entry point into the internal project memory.
It intentionally stays short and points to the focused documents below.

## What The Library Does

Annotation-driven Hazelcast client integration for Spring Boot applications.
It removes boilerplate around client bootstrapping, compact serialization
registration, IMap listener wiring, and Hibernate L2 cache setup.

Group: `io.github.javaquasar` | Java 17 | Hazelcast 5.5.0 | Spring Boot 2/3/4

## Read These Files

- [architecture.md](architecture.md) - module graph, key classes, auto-configuration flow, configuration model, conventions, test infrastructure
- [known-issues.md](known-issues.md) - technical debt, caveats, test pitfalls, Hibernate edge cases, resolved April 2026 history
- [positioning.md](positioning.md) - project overview, competitors, differentiators, target audience, roadmap-level strengths and weaknesses
- [release-process.md](release-process.md) - build commands, publishing, signing, release notes, process guidance

## Canonical Rules

- Treat this file as the root pointer, not the place for detailed long-form content.
- Put architecture facts in `architecture.md`.
- Put risks, caveats, and resolved history in `known-issues.md`.
- Put product framing and competitive analysis in `positioning.md`.
- Put build, publishing, and signing workflow in `release-process.md`.

## Quick Routing

- Need structure or class responsibilities: open [architecture.md](architecture.md)
- Need current caveats or historical edge cases: open [known-issues.md](known-issues.md)
- Need messaging, value proposition, or competitor context: open [positioning.md](positioning.md)
- Need build or release workflow: open [release-process.md](release-process.md)
