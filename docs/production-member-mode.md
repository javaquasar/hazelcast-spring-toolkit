# Production Member Mode

Hazelcast Toolkit is client-first by default. In most production services, a
Spring Boot application should connect to a separately managed Hazelcast
cluster as a client:

```properties
hazelcast.toolkit.instance.mode=client
```

Use member mode only when the application is intentionally part of the
Hazelcast cluster:

```properties
hazelcast.toolkit.instance.mode=member
```

## One-Property Topology Switch

Keep values needed by both topologies under `hazelcast.toolkit`:

```yaml
hazelcast:
  toolkit:
    cluster-name: platform-cache
    enterprise-license-key: ${HZ_ENTERPRISE_LICENSE_KEY:}
    network:
      seed-members:
        - 10.3.0.5:5071
        - 10.3.0.6:5071
        - 10.3.0.9:5071
    instance:
      mode: client
    member:
      network:
        port: 5072
        port-auto-increment: false
        join:
          auto-detection-enabled: false
          multicast-enabled: false
  client:
    network:
      smart-routing: true
```

After the mode-specific client and member options are prepared, switching the
application topology only changes:

```properties
hazelcast.toolkit.instance.mode=member
```

The shared cluster name, seed addresses, and Enterprise license are used by
both modes. Legacy `hazelcast.client.cluster-name`,
`hazelcast.client.network.cluster-members`,
`hazelcast.toolkit.member.cluster-name`, and
`hazelcast.toolkit.member.network.join.tcp-ip-members` remain supported when
their shared replacements are absent.

JCache and native Hibernate wiring follow the live instance automatically. For
native Hibernate modes, omit provider-specific instance properties or use the
single topology-neutral alias:

```properties
spring.jpa.properties.hibernate.cache.hazelcast.instance.name=<actual-hazelcast-instance-name>
```

The toolkit translates that alias to the Hazelcast Hibernate provider key
required by the active topology. A conflicting explicit native-client flag or
instance name stops startup with a configuration error.

## When To Use Member Mode

Member mode is appropriate when the Spring Boot process must own cluster data
and partition work directly. Typical cases are:

- legacy applications that already run embedded Hazelcast members
- tightly controlled platforms where the service lifecycle and Hazelcast member
  lifecycle are deployed together
- specialized workloads that need member-local execution or member-side
  listeners
- migration phases where separating the Hazelcast cluster from the application
  is not yet possible

Prefer client mode for ordinary horizontally scaled services. Client mode keeps
application replicas lightweight and lets the Hazelcast cluster scale,
restart, secure, and upgrade independently.

## Why This Is An Advanced Topology

In member mode, every Spring Boot application instance becomes a Hazelcast
cluster member. Scaling the deployment no longer only scales HTTP/application
capacity; it also changes the Hazelcast cluster size, partition table,
replication traffic, and failure domain.

Operational consequences:

- rolling deploys add and remove Hazelcast members
- readiness and termination behavior affect partition migration
- application crashes are also member failures
- CPU, heap, network, and GC pressure affect both application code and data-grid
  work
- port and discovery configuration must be correct for every replica
- security settings must match the rest of the cluster

Treat member mode as a deliberate topology decision, not as a drop-in
replacement for client mode.

## Kubernetes And Networking Warnings

Member mode needs stable member-to-member connectivity. In Kubernetes, verify
these points before using it in production:

- Use a StatefulSet or another deployment pattern that gives predictable pod
  identity when member discovery depends on stable DNS names.
- Expose Hazelcast member ports through the correct Service type. A headless
  Service is usually a better fit for member discovery than a load-balanced
  Service.
- Configure the Hazelcast member port explicitly and decide whether
  `port-auto-increment` is acceptable. Auto-increment is convenient locally but
  can hide production port conflicts.
- Make sure network policies allow member-to-member traffic on Hazelcast ports.
- Set `public-address` when pods advertise addresses that other members cannot
  use directly.
- Disable multicast in Kubernetes unless the platform explicitly supports it.
- Prefer DNS, TCP/IP, or the Hazelcast Kubernetes discovery plugin through an
  application-owned `HazelcastMemberConfigCustomizer`.

Minimal static DNS example:

```yaml
hazelcast:
  toolkit:
    cluster-name: platform-cache
    network:
      seed-members:
        - orders-service-0.orders-service-hz.default.svc.cluster.local:5701
        - orders-service-1.orders-service-hz.default.svc.cluster.local:5701
        - orders-service-2.orders-service-hz.default.svc.cluster.local:5701
    instance:
      mode: member
    member:
      instance-name: orders-service-member
      network:
        port: 5701
        port-auto-increment: false
        join:
          auto-detection-enabled: false
          multicast-enabled: false
```

For plugin-specific discovery settings, keep the toolkit properties for common
settings and use a customizer for the Hazelcast `Config` surface that is
specific to your platform:

```java
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastMemberConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HazelcastMemberDiscoveryConfiguration {

    @Bean
    HazelcastMemberConfigCustomizer kubernetesDiscoveryCustomizer() {
        return config -> {
            // Configure Hazelcast Kubernetes discovery plugin here.
            // Keep namespace, service name, labels, and RBAC choices
            // application-owned because they are platform-specific.
        };
    }
}
```

## TLS And Security Warnings

Member mode must use the same security posture as the rest of the Hazelcast
cluster.

- Do not run mixed secure and insecure members.
- Keep TLS keystores, truststores, passwords, and Enterprise license keys in the
  runtime secret store.
- Use a `HazelcastMemberConfigCustomizer` for TLS, credentials, security realms,
  or provider-specific security settings.
- Verify that client-mode and member-mode applications use compatible
  `cluster-name`, TLS, authentication, and serialization configuration before
  mixing them.
- Treat `/actuator/health`, metrics, and `/hz-toolkit` diagnostics as
  operational endpoints and secure them with the same rules as the rest of the
  service.

TLS customizer sketch:

```java
import com.hazelcast.config.SSLConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastMemberConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
class HazelcastMemberSecurityConfiguration {

    @Bean
    HazelcastMemberConfigCustomizer memberTlsCustomizer() {
        return config -> {
            Properties sslProperties = new Properties();
            sslProperties.setProperty("javax.net.ssl.keyStore", "/etc/hazelcast/tls/member.p12");
            sslProperties.setProperty("javax.net.ssl.keyStorePassword",
                    System.getenv("HZ_MEMBER_KEYSTORE_PASSWORD"));
            sslProperties.setProperty("javax.net.ssl.trustStore", "/etc/hazelcast/tls/truststore.p12");
            sslProperties.setProperty("javax.net.ssl.trustStorePassword",
                    System.getenv("HZ_MEMBER_TRUSTSTORE_PASSWORD"));

            config.getNetworkConfig()
                    .setSSLConfig(new SSLConfig()
                            .setEnabled(true)
                            .setProperties(sslProperties));
        };
    }
}
```

## Client Vs Member Vs Mixed

### Client Mode

A single Spring Boot application runs as a Hazelcast client and connects to an
external cluster.

```yaml
hazelcast:
  toolkit:
    cluster-name: platform-cache
    network:
      seed-members:
        - hazelcast-0.hazelcast.default.svc.cluster.local:5701
        - hazelcast-1.hazelcast.default.svc.cluster.local:5701
    instance:
      mode: client
  client:
    network:
      smart-routing: true
```

Use this for most services.

### Member Mode

A single Spring Boot application starts an embedded Hazelcast member.

```yaml
hazelcast:
  toolkit:
    cluster-name: platform-cache
    network:
      seed-members:
        - orders-service-0.orders-service-hz.default.svc.cluster.local:5701
        - orders-service-1.orders-service-hz.default.svc.cluster.local:5701
    instance:
      mode: member
    member:
      instance-name: orders-service-member
      network:
        port: 5701
        port-auto-increment: false
        join:
          auto-detection-enabled: false
          multicast-enabled: false
```

Use this only when the service is supposed to join the data-grid cluster as a
member.

### Mixed Mode

Two Spring Boot application instances use different modes against the same
cluster: one starts as a member, another connects as a client.

Member application:

```yaml
spring:
  application:
    name: orders-member

hazelcast:
  toolkit:
    cluster-name: platform-cache
    network:
      seed-members:
        - orders-member-0.orders-member-hz.default.svc.cluster.local:5701
    instance:
      mode: member
    member:
      instance-name: orders-member
      network:
        port: 5701
        port-auto-increment: false
        join:
          auto-detection-enabled: false
          multicast-enabled: false
```

Client application:

```yaml
spring:
  application:
    name: orders-api

hazelcast:
  toolkit:
    cluster-name: platform-cache
    network:
      seed-members:
        - orders-member-0.orders-member-hz.default.svc.cluster.local:5701
    instance:
      mode: client
```

Use mixed mode during migrations or for specialized topologies where one
application owns cluster membership and another consumes the same maps, caches,
and events as a client.

## Production Checklist

- Keep `hazelcast.toolkit.instance.mode` explicit in production configuration.
- Configure shared `hazelcast.toolkit.cluster-name` and
  `hazelcast.toolkit.network.seed-members` for switchable applications.
- Keep the cluster name identical across all clients and members that should
  join the same cluster.
- Remove provider-specific Hibernate `use_native_client`, `instance_name`, and
  `native_client_instance_name` settings before switching topology.
- Verify health details expose the expected `mode`, `clusterName`, and
  `memberCount`.
- Watch member count during rolling deploys.
- Size heap and CPU for both application work and Hazelcast member work.
- Test pod termination and graceful shutdown behavior before production rollout.
- Run client, member, and mixed-mode integration tests for the target topology.
