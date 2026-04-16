# Hazelcast Toolkit

Annotation-driven Hazelcast integration for Spring Boot applications.

This project helps you reduce Hazelcast boilerplate around:
- Hazelcast client bootstrapping
- Compact serialization registration
- `IMap` listener registration
- Spring Boot integration
- Hibernate L2 cache experiments and integration tests

## Status

The most complete integration today is `toolkit-spring-boot3`.

The repository already contains modules for Spring Boot 2 and 4, but Boot 3 is currently the main implemented path.

## Modules

- `toolkit-core`: public annotations such as `@HzCompact` and `@HzIMapListener`
- `toolkit-runtime`: shared Hazelcast runtime contracts and helpers
- `toolkit-scan-api`: scanner abstraction
- `toolkit-scan-reflections`: `org.reflections`-based scanner implementation
- `toolkit-spring-boot2`: Spring Boot 2 integration module
- `toolkit-spring-boot3`: Spring Boot 3 integration module
- `toolkit-spring-boot4`: Spring Boot 4 integration module (opt-in via `-PenableBoot4=true`)
- `toolkit-metrics-spring`: optional Spring metrics/controller integration
- `toolkit-testcontainers`: shared Testcontainers support for integration tests

## Features

- Annotate compact types with `@HzCompact`
- Register either zero-config compact classes or explicit `CompactSerializer` implementations
- Auto-register `EntryListener` beans with `@HzIMapListener`
- Apply additional Hazelcast client tuning through `HazelcastClientConfigCustomizer`
- Reuse shared Hazelcast/Postgres Testcontainers infrastructure in integration tests

## Spring Boot 3 Quick Start

Add the Boot 3 module to your project and configure the Hazelcast client.

### Configuration

```yaml
spring:
  application:
    name: my-service

hazelcast:
  client:
    instance-name: app-hz-client
    cluster-name: dev
    network:
      cluster-members:
        - 127.0.0.1:5701
      smart-routing: true
  toolkit:
    client:
      base-name: hz.client
    compact:
      base-package: com.example.app.hazelcast
    metrics:
      enabled: false
```

### Hazelcast Client Naming

The toolkit can derive the final Hazelcast client name from two inputs:

- `hazelcast.toolkit.client.base-name`
- `spring.application.name`

Behavior:

- If both are present, the final client name becomes `<base-name>-<sanitized-application-name>`.
- If `spring.application.name` is missing or blank, the base name is used as-is.
- If `hazelcast.toolkit.client.base-name` is not configured, the toolkit falls back to `hazelcast.client.instance-name` for backward compatibility.
- Application names are lowercased and sanitized by replacing unsupported characters with `-`.

Examples:

- base `hz.client` + app `my-service` -> `hz.client-my-service`
- base `hz.client` + app `Billing/API @ EU` -> `hz.client-billing-api-eu`
- base `app-hz-client` + missing app name -> `app-hz-client`

### What Boot 3 Auto-Config Does
`toolkit-spring-boot3` currently provides:
- a `HazelcastInstance` client bean
- compact type scanning from `hazelcast.toolkit.compact.base-package`
- `@HzIMapListener` bean registration
- ordered `HazelcastClientConfigCustomizer` application
- optional metrics controller wiring

## Compact Serialization

### Zero-Config Compact

If you only need Hazelcast reflective compact serialization, annotate the class with `@HzCompact`.

```java
import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact
public class UserProfile {
    private String userId;
    private String nickname;

    public UserProfile() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
```

### Explicit CompactSerializer

If a class needs custom compact serialization, declare the serializer in `@HzCompact`.

```java
import io.github.javaquasar.hazelcast.toolkit.annotation.HzCompact;

@HzCompact(serializer = UserLimitEntryCompactSerializer.class)
public class UserLimitEntry {
    private LimitPeriod period;
    private Integer limitAmount;
    private Long startMillis;

    public LimitPeriod getPeriod() {
        return period;
    }

    public void setPeriod(LimitPeriod period) {
        this.period = period;
    }

    public Integer getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(Integer limitAmount) {
        this.limitAmount = limitAmount;
    }

    public Long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(Long startMillis) {
        this.startMillis = startMillis;
    }
}
```

```java
public enum LimitPeriod {
    DAILY(1),
    WEEKLY(2),
    MONTHLY(3);

    private final int id;

    LimitPeriod(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static LimitPeriod fromId(int id) {
        for (LimitPeriod value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown id: " + id);
    }
}
```

```java
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;

public class UserLimitEntryCompactSerializer implements CompactSerializer<UserLimitEntry> {

    @Override
    public void write(CompactWriter writer, UserLimitEntry object) {
        writer.writeInt32("periodId", object.getPeriod() != null ? object.getPeriod().getId() : 0);
        writer.writeInt32("limitAmount", object.getLimitAmount() == null ? 0 : object.getLimitAmount());
        writer.writeInt64("startMillis", object.getStartMillis() == null ? 0L : object.getStartMillis());
    }

    @Override
    public UserLimitEntry read(CompactReader reader) {
        UserLimitEntry object = new UserLimitEntry();
        int periodId = reader.readInt32("periodId");
        object.setPeriod(periodId == 0 ? null : LimitPeriod.fromId(periodId));
        object.setLimitAmount(reader.readInt32("limitAmount"));
        object.setStartMillis(reader.readInt64("startMillis"));
        return object;
    }

    @Override
    public String getTypeName() {
        return "UserLimitEntry";
    }

    @Override
    public Class<UserLimitEntry> getCompactClass() {
        return UserLimitEntry.class;
    }
}
```

Notes:
- Explicit serializers are registered before zero-config compact classes.
- The toolkit validates that `serializer.getCompactClass()` matches the annotated class.
- Serializers must have a no-args constructor.

## Hazelcast Client Customization

Shared client tuning can be plugged in through `HazelcastClientConfigCustomizer` from `toolkit-runtime`.

```java
import com.hazelcast.client.config.ClientConfig;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastClientConfigCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class HazelcastClientTuning implements HazelcastClientConfigCustomizer {

    @Override
    public void customize(ClientConfig clientConfig) {
        clientConfig.setProperty("hazelcast.client.statistics.enabled", "true");
    }
}
```

All detected customizers are applied in Spring order.

## IMap Listener Registration

Annotate a Spring bean that implements `EntryListener` with `@HzIMapListener`.

```java
import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzIMapListener;
import org.springframework.stereotype.Component;

@Component
@HzIMapListener(map = "users", includeValue = true)
public class UsersAddedListener implements EntryAddedListener<String, String> {

    @Override
    public void entryAdded(EntryEvent<String, String> event) {
        System.out.println("Added user: " + event.getKey());
    }
}
```

Notes:
- `localOnly = true` uses local listener registration.
- Listener beans are resolved from the Spring context, so proxies are supported.

## Tests

The repository contains focused tests for:
- `@HzCompact` registration in `toolkit-runtime`
- explicit compact serializer scanning and validation in `toolkit-spring-boot3`
- Boot 3 Hazelcast/Postgres integration with shared Testcontainers setup
- Hibernate L2 cache experiments against Hazelcast + PostgreSQL

Useful commands:

```bash
./gradlew build
./gradlew :toolkit-runtime:test --tests io.github.javaquasar.hazelcast.toolkit.hazelcast.CompactRegistrationTest
./gradlew :toolkit-spring-boot3:test --tests io.github.javaquasar.hazelcast.toolkit.springboot3.config.HazelcastClientFactoryTest
```

## Build and Release

Local build:

```bash
./gradlew build
```

Enable the optional Spring Boot 4 module when Boot 4 dependencies are available locally:

```bash
./gradlew -PenableBoot4=true :toolkit-spring-boot4:compileJava
```

This keeps the default build stable while Boot 4 support is still being developed.

### Published Modules

The current release configuration publishes these library modules:
- `toolkit-core`
- `toolkit-runtime`
- `toolkit-scan-api`
- `toolkit-scan-reflections`
- `toolkit-metrics-spring`
- `toolkit-spring-common`
- `toolkit-spring-boot2`
- `toolkit-spring-boot3`

The test support module `toolkit-testcontainers` is intentionally not published.

### Publication Commands

Build and verify everything before release:

```bash
./gradlew build
```

Publish one module into its local staging Maven repository:

```bash
./gradlew :toolkit-core:publishMavenJavaPublicationToLocalStagingRepository -PreleaseVersion=0.1.0
```

Create a Central Portal bundle ZIP for one module:

```bash
./gradlew :toolkit-core:centralBundleZip -PreleaseVersion=0.1.0
```

Create bundle ZIPs for all published modules:

```bash
./gradlew centralBundleAll -PreleaseVersion=0.1.0
```

If signing is enabled, provide the keys through Gradle properties, for example in `~/.gradle/gradle.properties`:

```properties
signingKey=...
signingPassword=...
```

Generated staging repositories and ZIP bundles are written under each published module's `build` directory.

## Current Notes

- Boot 3 is the primary supported integration path in the current codebase.
- Boot 2 and Boot 4 modules are present for future parity work.
- The project is modular on purpose so common runtime contracts can be reused across framework variants.




