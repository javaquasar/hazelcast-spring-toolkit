# Production Recipes

These recipes are starting points for services that use Hazelcast Toolkit in
production. They focus on predictable client wiring, observability, and rollout
behavior.

## Multi-Service Client Setup

Use the same cluster name and member list across services that should share
maps, JCache caches, and Hibernate L2 regions.

```properties
spring.application.name=orders-service

hazelcast.client.cluster-name=platform-cache
hazelcast.client.network.cluster-members[0]=hazelcast-0.hz:5701
hazelcast.client.network.cluster-members[1]=hazelcast-1.hz:5701
hazelcast.client.network.cluster-members[2]=hazelcast-2.hz:5701
hazelcast.client.network.smart-routing=true
```

Recommended defaults:

| Area | Recommendation |
|---|---|
| Cluster identity | Keep `cluster-name` explicit in every service. |
| Addresses | Use stable service DNS names or an environment-provided member list. |
| Startup | Prefer synchronous startup for cache-dependent services. |
| Health | Enable toolkit health and include `hazelcastToolkit` in readiness. |
| Metrics | Enable Micrometer metrics for near-cache and Hibernate L2 visibility. |

## Kubernetes Discovery

The most portable Kubernetes setup is DNS-based: expose Hazelcast members
through a stable Service or headless Service and feed those names into
`hazelcast.client.network.cluster-members`.

```yaml
spring:
  application:
    name: orders-service

hazelcast:
  client:
    cluster-name: platform-cache
    network:
      smart-routing: true
      cluster-members:
        - hazelcast.platform-cache.svc.cluster.local:5701
```

For StatefulSet-style member DNS, list the stable pod hostnames:

```yaml
hazelcast:
  client:
    cluster-name: platform-cache
    network:
      cluster-members:
        - hazelcast-0.hazelcast.platform-cache.svc.cluster.local:5701
        - hazelcast-1.hazelcast.platform-cache.svc.cluster.local:5701
        - hazelcast-2.hazelcast.platform-cache.svc.cluster.local:5701
```

If the service uses Hazelcast's Kubernetes discovery plugin instead of static
DNS, keep the starter's base properties for common settings and add a
`HazelcastClientConfigCustomizer` bean for plugin-specific `ClientConfig`
options. Keep that customizer application-owned, because namespace, service
name, labels, and RBAC differ by platform.

## TLS And Security Customizers

The starter intentionally keeps low-level security options out of the common
property model. Register application-owned `HazelcastClientConfigCustomizer`
beans for TLS, credentials, labels, or provider-specific security settings.

Example TLS customizer:

```java
import com.hazelcast.config.SSLConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.Properties;

@Configuration
class HazelcastClientSecurityConfiguration {

    @Bean
    @Order(100)
    HazelcastClientConfigCustomizer hazelcastTlsCustomizer() {
        return clientConfig -> {
            Properties sslProperties = new Properties();
            sslProperties.setProperty("javax.net.ssl.keyStore", "/etc/hazelcast/tls/client.p12");
            sslProperties.setProperty("javax.net.ssl.keyStorePassword",
                    System.getenv("HZ_CLIENT_KEYSTORE_PASSWORD"));
            sslProperties.setProperty("javax.net.ssl.trustStore", "/etc/hazelcast/tls/truststore.p12");
            sslProperties.setProperty("javax.net.ssl.trustStorePassword",
                    System.getenv("HZ_CLIENT_TRUSTSTORE_PASSWORD"));

            clientConfig.getNetworkConfig()
                    .setSSLConfig(new SSLConfig()
                            .setEnabled(true)
                            .setProperties(sslProperties));
        };
    }
}
```

Operational notes:

- Mount keystores and truststores as Kubernetes Secrets or equivalent platform
  secrets.
- Do not commit passwords into `application.yml`; inject them from the runtime
  secret store.
- Keep Enterprise license keys in `hazelcast.client.enterprise-license-key`
  backed by an environment variable or secret.
- Prefer one small customizer per concern, ordered explicitly when one depends
  on another.

## Services-Style Observability Baseline

```properties
management.endpoints.web.exposure.include=health,metrics,info,prometheus
management.endpoint.health.show-details=when_authorized
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.readiness.include=readinessState,hazelcastToolkit

hazelcast.toolkit.health.enabled=true
hazelcast.toolkit.metrics.enabled=true
```

Add the active near-cache probe only for services where a stable cacheable JPA
entity is available:

```properties
management.endpoints.web.exposure.include=health,metrics,info,prometheus,hazelcastNearCache

hazelcast.toolkit.actuator.near-cache-check.enabled=true
hazelcast.toolkit.actuator.near-cache-check.entity-class=com.mycompany.domain.ReferenceEntity
hazelcast.toolkit.actuator.near-cache-check.entity-id=1
```

Keep `/hz-toolkit/...` diagnostics opt-in and secured:

```properties
hazelcast.toolkit.metrics.diagnostic-endpoint.enabled=true
```

## Rolling Deploy Near-Cache Checklist

Before rollout:

- Verify `hazelcastToolkit` readiness is `UP` in the current release.
- Check expected near-cache meters are present for hot maps/caches.
- Confirm `hazelcast.toolkit.hibernate.l2.use-statistics=true` when relying on
  Hibernate L2 hit/miss dashboards.
- Confirm cache mode choices are intentional:
  `spring-cache.mode` for Spring Cache, `hibernate.l2.region-factory` for
  Hibernate L2.

During rollout:

- Watch `hazelcast_toolkit_near_cache_misses_total` for temporary warm-up spikes.
- Watch `hazelcast_toolkit_near_cache_invalidations_total` for unusual churn.
- Watch `/actuator/health` readiness rather than only process liveness.
- Avoid tight polling of `/actuator/hazelcastNearCache`; use it as an active
  manual or low-frequency verification probe.

After rollout:

- Compare near-cache hit ratio against the previous release window.
- Confirm Hibernate L2 hit/miss metrics still have the expected `regionFactory`
  tag.
- Use `/hz-toolkit/hz/jcache/near-stats/{cacheName}` or
  `/hz-toolkit/hz/map/near-stats/{mapName}` only while investigating a concrete
  cache/map question.

## Native Spring Cache Migration Recipe

For legacy Spring Boot 2 services that used Hazelcast's native Spring cache
manager:

```properties
hazelcast.toolkit.spring-cache.mode=native
```

Keep Hibernate L2 on JCache when that is the current persistence contract:

```properties
hazelcast.toolkit.hibernate.l2.region-factory=JCACHE
```

This combination lets application-level `@Cacheable` code use
`com.hazelcast.spring.cache.HazelcastCacheManager` while Hibernate L2 continues
to use the toolkit-managed `javax.cache.CacheManager`.

See `example-spring-boot2` for a minimal Boot 2 legacy native-cache smoke test.

## Related Guides

- [Actuator operations](actuator-operations.md)
- [Observability](observability.md)
- [Prometheus and Grafana examples](prometheus-grafana-examples.md)
- [Cache modes maturity](cache-modes-maturity.md)
- [Compatibility matrix](compatibility-matrix.md)
