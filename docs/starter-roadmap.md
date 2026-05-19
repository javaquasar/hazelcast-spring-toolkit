# Hazelcast Toolkit Starter Roadmap

This roadmap focuses on making the Spring Boot starters more reliable,
observable, and pleasant to operate in real services.

## Direction

The starter should keep moving toward:

- less hidden behavior
- clearer auto-configuration contracts
- stronger diagnostics
- stable observability surfaces
- simple production recipes for common Hazelcast client setups

## 0.4.0: Consumer-Grade Observability

Focus: make observability useful as a production surface, not only as debugging
support.

Planned work:

- treat documented metric names as a stable API
- add Prometheus / Grafana examples
- add actuator endpoint examples for operations teams
- add health/readiness indicators for Hazelcast client connectivity
- add diagnostics for "cache exists but Near Cache is disabled"

Candidate property:

```properties
hazelcast.toolkit.health.enabled=true
```

Candidate indicators:

- Hazelcast client lifecycle state (first slice added in `0.4.0`)
- cluster connection status (first slice added in `0.4.0`)
- optional cache/map availability checks
- Hibernate L2 readiness where applicable

Goal:

Applications should be able to answer: "Is this service ready to use Hazelcast
right now?"

## 0.5.0: Starter Polish

Focus: make the starter behavior more predictable and easier to reason about.

Planned work:

- add broader `@ConditionalOn...` tests for Boot 2, Boot 3, and Boot 4
- improve fail-fast errors for missing optional dependencies
- publish a clear Boot / Hibernate / Hazelcast compatibility table
- complete and validate Spring Boot configuration metadata coverage
- consider metadata validation in tests

Goal:

Unexpected classpath or property combinations should fail clearly, back off
cleanly, or be covered by tests.

## 0.6.0: Cache Modes Maturity

Focus: make Spring Cache and Hibernate L2 mode choices easier for legacy and
modern services.

Planned work:

- [x] add migration recipes for JCache to native Spring Cache mode
- [x] add more Boot 2 legacy-service examples
- [x] strengthen tests for user-provided `CacheManager` beans
- [x] document behavior when both JCache and native Hazelcast Spring Cache are on
  the classpath
- [x] clarify interaction between `spring-cache.mode` and
  `hibernate.l2.region-factory`

Goal:

Teams should be able to choose cache modes intentionally instead of discovering
behavior by trial and error.

## 0.7.0 And Later: Production Recipes

Focus: provide ready-to-adapt operational patterns.

Possible recipes:

- [x] multi-service Hazelcast client setup
- [x] Kubernetes discovery examples
- [x] TLS and security config customizers
- [x] rolling deploy and near-cache invalidation checklist
- performance comparison examples for Hibernate L2 modes
- example dashboards and alert rules

Goal:

The project should feel like a practical starter kit for production Hazelcast
clients, not only a set of auto-configuration classes.
