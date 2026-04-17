# CLAUDE.md

This is the short AI entrypoint for this repository.

Use the documents below instead of duplicating long-form project knowledge here:

- `README.md` - public entrypoint for humans: project overview, quick start, modules, and basic usage
- `.claude/project-knowledge-base.md` - canonical internal knowledge hub
- `.claude/architecture.md` - module graph, key classes, auto-configuration flow, conventions, test infrastructure
- `.claude/known-issues.md` - technical debt, caveats, Hibernate L2 notes, resolved history
- `.claude/positioning.md` - value proposition, differentiators, competitor context
- `.claude/release-process.md` - build, publishing, signing, release workflow
- `.claude/analysis-what-to-improve-now.md` - active backlog only

## Working Rules

- Keep `README.md` human-focused and public.
- Keep this file short and stable.
- Put detailed internal project memory in `.claude/`.
- Do not duplicate architecture, release, or known-issue details across multiple entrypoint files.
- If a task is marked resolved in `.claude/known-issues.md`, do not treat it as active roadmap work.

## Repository Defaults

- Primary supported path: Spring Boot 3
- Boot 4 is opt-in via `-PenableBoot4=true`
- Java toolchain: Java 17
- Tests use JUnit Platform

## Fast Start

```bash
./gradlew build
./gradlew :toolkit-spring-boot3:test
./gradlew -PenableBoot4=true :toolkit-spring-boot4:compileJava
```
