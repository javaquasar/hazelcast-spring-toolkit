# Roadmap v0.10.0: Hazelcast Instance Modes

This roadmap captures the idea of extending Hazelcast Toolkit beyond the current
client-first model by adding an explicit opt-in member mode.

## Motivation

Hazelcast Toolkit currently focuses on client mode: a Spring Boot service creates
a Hazelcast client and connects to an external Hazelcast cluster.

Some legacy applications historically run as embedded Hazelcast members. For
those services, migration to the toolkit would be easier if the starter could
create either:

- a Hazelcast client
- a Hazelcast member

The default must remain client mode.

## Reference: Spring Boot Hazelcast Integration

Spring Boot's official Hazelcast module is a useful reference for how to split
client and member creation while still respecting user-provided beans.

Primary repository reference:

- [spring-projects/spring-boot Hazelcast module](https://github.com/spring-projects/spring-boot/tree/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast)

Important source files:

| File | Why it matters |
|---|---|
| [HazelcastAutoConfiguration.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastAutoConfiguration.java) | Small top-level auto-configuration that imports client and server/member branches. |
| [HazelcastClientConfiguration.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastClientConfiguration.java) | Client branch guarded by Hazelcast client classes and missing `HazelcastInstance`. |
| [HazelcastClientInstanceConfiguration.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastClientInstanceConfiguration.java) | Starts a client from resolved connection details. |
| [HazelcastConnectionDetails.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastConnectionDetails.java) | Abstraction for resolving `ClientConfig`; useful inspiration for future Docker/Testcontainers/Kubernetes support. |
| [HazelcastConnectionDetailsConfiguration.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastConnectionDetailsConfiguration.java) | Shows precedence between explicit `ClientConfig`, properties, and connection details. |
| [PropertiesHazelcastConnectionDetails.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/PropertiesHazelcastConnectionDetails.java) | Loads XML/YAML client config and sets the application class loader. |
| [HazelcastServerConfiguration.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastServerConfiguration.java) | Member/server branch, `Config` customizers, Spring managed context, SLF4J logging, and reuse by instance name. |
| [HazelcastConfigCustomizer.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastConfigCustomizer.java) | Functional customizer interface for member-side `Config`. |
| [HazelcastConfigResourceCondition.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastConfigResourceCondition.java) | Resource/system-property condition logic. Useful as reference, but not something to copy blindly. |
| [HazelcastClientConfigAvailableCondition.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastClientConfigAvailableCondition.java) | Detects whether a config file is client config. Useful background for config-file support. |
| [HazelcastHealthIndicator.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/health/HazelcastHealthIndicator.java) | Minimal lifecycle-based health indicator. |
| [HazelcastHealthContributorAutoConfiguration.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/autoconfigure/health/HazelcastHealthContributorAutoConfiguration.java) | Health auto-configuration that activates only when a `HazelcastInstance` exists. |
| [HazelcastDockerComposeConnectionDetailsFactory.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/docker/compose/HazelcastDockerComposeConnectionDetailsFactory.java) | Docker Compose connection-details idea for future developer-experience support. |
| [HazelcastContainerConnectionDetailsFactory.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/main/java/org/springframework/boot/hazelcast/testcontainers/HazelcastContainerConnectionDetailsFactory.java) | Testcontainers connection-details idea for future starter tests. |
| [HazelcastCacheConfiguration.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-cache/src/main/java/org/springframework/boot/cache/autoconfigure/HazelcastCacheConfiguration.java) | Cache auto-configuration reuses the single candidate `HazelcastInstance`. |

Important test references:

| File | Useful coverage idea |
|---|---|
| [HazelcastAutoConfigurationClientTests.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/test/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastAutoConfigurationClientTests.java) | Client config precedence, user `ClientConfig`, connection details, class loader, and instance-name behavior. |
| [HazelcastAutoConfigurationServerTests.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/test/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastAutoConfigurationServerTests.java) | Member config files, `Config` bean precedence, Spring managed context, logging customizer, and customizer order. |
| [HazelcastAutoConfigurationTests.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/test/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastAutoConfigurationTests.java) | Default config priority checks. |
| [HazelcastClientConfigAvailableConditionTests.java](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-hazelcast/src/test/java/org/springframework/boot/hazelcast/autoconfigure/HazelcastClientConfigAvailableConditionTests.java) | Negative tests for wrong or missing config resources. |

### Spring Boot Patterns To Reuse

Keep these ideas for the toolkit design:

- separate auto-configuration branches for client and member/server instance creation
- always back off when the application provides a `HazelcastInstance`
- accept user-provided config beans as higher priority than generated config
- use ordered customizers instead of exposing every Hazelcast setting as toolkit properties
- set the application class loader on generated Hazelcast configs
- support `SpringManagedContext` for member mode when `hazelcast-spring` is present
- set `hazelcast.logging.type=slf4j` by default, but only when the user did not set it
- create health and cache features on top of the resulting single `HazelcastInstance`
- use `ApplicationContextRunner` tests for auto-configuration contracts

### Spring Boot Snippets Worth Mirroring

Top-level auto-configuration is intentionally small and imports both branches:

```java
@AutoConfiguration
@ConditionalOnClass(HazelcastInstance.class)
@EnableConfigurationProperties(HazelcastProperties.class)
@Import({ HazelcastClientConfiguration.class, HazelcastServerConfiguration.class })
public final class HazelcastAutoConfiguration {
}
```

Client branch backs off when a `HazelcastInstance` already exists:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HazelcastClient.class)
@ConditionalOnMissingBean(HazelcastInstance.class)
@Import({ HazelcastConnectionDetailsConfiguration.class, HazelcastClientInstanceConfiguration.class })
class HazelcastClientConfiguration {
}
```

Client instance creation chooses `getOrCreate` only when an instance name is set:

```java
@Bean
HazelcastInstance hazelcastInstance(HazelcastConnectionDetails hazelcastConnectionDetails) {
    ClientConfig config = hazelcastConnectionDetails.getClientConfig();
    return (!StringUtils.hasText(config.getInstanceName()))
            ? HazelcastClient.newHazelcastClient(config)
            : HazelcastClient.getOrCreateHazelcastClient(config);
}
```

Member creation uses the same instance-name rule:

```java
private static HazelcastInstance getHazelcastInstance(Config config) {
    if (StringUtils.hasText(config.getInstanceName())) {
        return Hazelcast.getOrCreateHazelcastInstance(config);
    }
    return Hazelcast.newHazelcastInstance(config);
}
```

Member config customizers are ordered and applied before startup:

```java
hazelcastConfigCustomizers.orderedStream()
        .forEach((customizer) -> customizer.customize(config));
```

Spring-managed context support is added as a config customizer:

```java
@Bean
@Order(0)
HazelcastConfigCustomizer springManagedContextHazelcastConfigCustomizer(
        ApplicationContext applicationContext) {
    return (config) -> {
        SpringManagedContext managementContext = new SpringManagedContext();
        managementContext.setApplicationContext(applicationContext);
        config.setManagedContext(managementContext);
    };
}
```

Default Hazelcast logging is set without overwriting user configuration:

```java
if (!config.getProperties().containsKey("hazelcast.logging.type")) {
    config.setProperty("hazelcast.logging.type", "slf4j");
}
```

Health checks should depend on lifecycle, not on client/member implementation
details:

```java
if (!this.hazelcast.getLifecycleService().isRunning()) {
    builder.down();
    return;
}
```

### Spring Boot Ideas To Avoid Copying Directly

Do not copy Spring Boot's implicit mode detection from `hazelcast.xml` vs
`hazelcast-client.xml` as the primary behavior. Hazelcast Toolkit should keep
the mode explicit:

```properties
hazelcast.toolkit.instance.mode=client
hazelcast.toolkit.instance.mode=member
hazelcast.toolkit.instance.mode=none
```

This preserves the current client-first contract and makes production behavior
obvious in service configuration.

## Proposed Property Model

```properties
hazelcast.toolkit.instance.mode=client
```

Candidate values:

| Mode | Behavior |
|---|---|
| `client` | Default. Creates `HazelcastClient.newHazelcastClient(ClientConfig)`. |
| `member` | Creates `Hazelcast.newHazelcastInstance(Config)`. |
| `none` | Optional future mode. The starter does not create a `HazelcastInstance`; the application provides one. |

## Proposed APIs

Keep the existing client customizer:

```java
HazelcastClientConfigCustomizer
```

Add a member-side customizer:

```java
HazelcastMemberConfigCustomizer
```

This lets applications configure member-specific settings without forcing the
starter to expose the whole Hazelcast member `Config` surface as properties.

## Auto-Configuration Shape

Split instance creation clearly:

- client auto-configuration activates when `instance.mode=client`
- member auto-configuration activates when `instance.mode=member`
- shared feature auto-configurations depend only on the resulting
  `HazelcastInstance`

Shared features should continue to work on top of either mode where supported:

- Compact registration
- IMap listener registration
- Spring Cache mode
- JCache manager
- Hibernate L2
- Micrometer metrics
- health indicator
- diagnostic endpoints

## First Implementation Slice

Status: implemented in the v0.10.0 development branch, including Boot 3
runtime hardening coverage for client, member, and mixed topologies.

1. Add `InstanceMode` enum to toolkit properties.
2. Keep `CLIENT` as the default.
3. Add `HazelcastMemberConfigCustomizer`.
4. Add `HazelcastMemberFactory` or equivalent member-mode support class for
   building and starting a Hazelcast `Config`.
5. Add optional member-mode customizers for `SpringManagedContext` and
   `hazelcast.logging.type=slf4j`.
6. Change auto-configuration to use `@ConditionalOnMissingBean(HazelcastInstance.class)`
   instead of relying only on the bean name `hazelcastInstance`.
7. Add Boot 2, Boot 3, and Boot 4 auto-configuration tests proving:
   - client mode remains default
   - member mode creates a member-backed `HazelcastInstance`
   - customizers are applied
   - only one `HazelcastInstance` bean is created
8. Add docs explaining that member mode is opt-in.

## Starter Test Plan

Add starter-level tests for every supported Spring Boot generation.

### Boot 2 / Boot 3 / Boot 4 Auto-Configuration Tests

For each starter module:

- default mode creates a client-backed `HazelcastInstance`
- `hazelcast.toolkit.instance.mode=client` behaves the same as the default
- `hazelcast.toolkit.instance.mode=member` creates a member-backed
  `HazelcastInstance`
- `hazelcast.toolkit.instance.mode=none`, if included in the first slice, backs
  off and lets an application-provided `HazelcastInstance` win
- only one `HazelcastInstance` bean is present in each mode
- client customizers are applied only in client mode
- member customizers are applied only in member mode
- invalid mode/classpath combinations fail with clear messages

### Shared Feature Tests

Verify that existing starter features still work with member mode where
supported:

- Compact scanning registers reflective classes and explicit serializers
- IMap listener auto-registration uses the member-backed instance
- JCache auto-configuration works on top of the member-backed instance
- Spring Cache `jcache` mode still exposes `JCacheCacheManager`
- Spring Cache `native` mode still exposes Hazelcast's native Spring
  `HazelcastCacheManager`
- Hibernate L2 `JCACHE` can use the toolkit-managed JCache manager
- metrics binders do not assume client-only topology
- health indicator reports `mode=member`

### Regression Tests

Keep existing client-mode behavior locked down:

- current client-mode tests must continue to pass unchanged
- default property behavior must remain client-first
- existing consumer examples must not start embedded members unless they opt in
- optional dependencies must not become mandatory for default client mode

### Instance Mode Integration Tests

Add integration tests that model how real services are deployed against a small
Hazelcast cluster.

#### Client Mode Scenario

Topology:

- one external Hazelcast member node
- one Spring Boot application context started with
  `hazelcast.toolkit.instance.mode=client`
- the application connects as a Hazelcast client to the external member

Verify:

- the external member sees one client connection
- the application does not start an embedded Hazelcast member
- the application uses the configured cluster name
- writes through the application are visible in the external member
- JCache and Spring Cache behavior works through the client-backed instance
- listeners, metrics, and health checks work in client mode

#### Member Mode Scenario

Topology:

- one existing Hazelcast member node
- one Spring Boot application context started with
  `hazelcast.toolkit.instance.mode=member`
- the application joins the same Hazelcast cluster as an additional member

Verify:

- the cluster eventually has two members: the original member plus the
  application member
- the application uses the configured cluster name and discovery settings
- writes through the application member are visible from the original member
- member-mode health reports `mode=member`
- metrics and listeners work with member-backed `HazelcastInstance`
- shutdown of the application removes only that member and leaves the original
  member running

#### Mixed Mode Scenario

Topology:

- one existing Hazelcast member node
- one Spring Boot application context started with
  `hazelcast.toolkit.instance.mode=client`
- one Spring Boot application context started with
  `hazelcast.toolkit.instance.mode=member`
- both application instances use the same cluster name

Verify:

- the cluster eventually has two members: the original member plus the
  application member
- the original member sees one application client connection
- writes from the client-mode application are visible to the member-mode
  application
- writes from the member-mode application are visible to the client-mode
  application
- health reports `mode=client` for the client context and `mode=member` for the
  member context
- cache, listener, and metrics behavior remains consistent across mixed
  topology
- shutting down the member-mode application removes only that member; the
  client-mode application can still use the original member

These tests should avoid large clusters by default. Use one external member and
at most two application contexts as the baseline so the scenario stays stable in
CI.

## Compatibility Areas To Verify

| Area | Verification |
|---|---|
| JCache | JCache manager works when backed by member mode. |
| Spring Cache `jcache` | Spring `JCacheCacheManager` still works. |
| Spring Cache `native` | Hazelcast native Spring `HazelcastCacheManager` works with the member instance. |
| Hibernate L2 `JCACHE` | Hibernate can use the toolkit-managed JCache manager. |
| Hibernate native modes | `HAZELCAST_LOCAL` and `HAZELCAST` behavior is documented and tested where supported. |
| Metrics | Micrometer binders do not assume client-only topology. |
| Health | Health details include `mode=client` or `mode=member`. |

## Production Warnings

Member mode should be documented as an advanced opt-in mode.

Important warnings:

- every application instance becomes a Hazelcast cluster member
- rolling deploys change cluster membership
- Kubernetes discovery, port configuration, and split-brain protection matter
- TLS/security configuration is more critical than in simple client mode
- member mode may not be appropriate for ordinary horizontally scaled
  microservices

## Documentation To Add

- README section: `Hazelcast Instance Mode`
- production recipe: member mode deployment notes
- compatibility matrix row for client/member support
- release notes for the first version that introduces the feature

## Open Questions

- Should the first release support `none` mode, or keep that for a later slice?
- Should member mode expose a minimal property model, or require customizers for
  most member-specific settings?
- Should member mode live in the same starter artifacts or an optional add-on
  starter?
- How much Hibernate native-mode coverage is required before calling member
  mode production-ready?
