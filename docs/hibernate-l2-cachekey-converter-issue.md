# Hibernate L2 Cache CacheKey + AttributeConverter Issue

## Problem statement

When Hibernate second-level cache is backed by Hazelcast via JCache, an entity that uses a composite `@Embeddable` key can fail during cache-key serialization if that key contains both:

- a `@ManyToOne` association
- a field mapped with `@Convert`

The observed runtime failure is:

```text
com.hazelcast.nio.serialization.HazelcastSerializationException: Failed to serialize 'org.hibernate.cache.internal.CacheKeyImplementation'
Caused by: java.io.NotSerializableException: org.hibernate.metamodel.model.convert.internal.JpaAttributeConverterImpl
```

This repository now contains automated tests that reproduce and isolate the behavior.

## Minimal reproduction

### Hibernate 5 / Spring Boot 2 reproduction

The minimal failing reproduction lives in:

- `toolkit-spring-boot2/src/test/java/io/github/javaquasar/hazelcast/toolkit/boot2/Boot2HibernateL2CacheKeyIssueIntegrationTest.java`
- `toolkit-spring-boot2/src/test/java/io/github/javaquasar/hazelcast/toolkit/boot2/l2issue/*`

The failing entity shape is:

```java
@Embeddable
public class LegacyIssueUserGroupPkWithConverter implements Serializable {
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private LegacyIssueUser user;

    @Column(name = "type_id", nullable = false)
    @Convert(converter = LegacyIssueUserGroupTypeConverter.class)
    private LegacyIssueUserGroupType type;
}
```

```java
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "legacy-l2-issue-group-with-converter")
public class LegacyIssueUserGroupWithConverter {
    @EmbeddedId
    private LegacyIssueUserGroupPkWithConverter id;
}
```

Contrary to the initial expectation, the failure happens during `persist(...)`, not only on the second cached read. Hibernate asks the cache layer about transient state, builds a `CacheKeyImplementation`, and Hazelcast then fails to serialize that key because the key path still references Hibernate converter metadata.

### Hibernate 6 / Spring Boot 3 comparison

The same mapping matrix is covered in:

- `toolkit-spring-boot3/src/test/java/io/github/javaquasar/hazelcast/toolkit/boot3/Boot3HibernateL2CacheKeyIssueIntegrationTest.java`
- `toolkit-spring-boot3/src/test/java/io/github/javaquasar/hazelcast/toolkit/boot3/l2issue/*`

On the current Boot 3 / Hibernate 6 stack used by this repository, the same composite-key + `@ManyToOne` + `@Convert` mapping does **not** reproduce the serialization failure. The entity persists successfully and produces second-level cache hits.

## Mapping matrix

### Spring Boot 2.7 / Hibernate 5.6

| Case | Mapping | Result |
|---|---|---|
| A | Composite `@Embeddable` key + `@ManyToOne` + `@Convert` | Fails |
| B | Composite `@Embeddable` key without converter (scalar key fields) | Succeeds |
| C | Simple primary key + `@Convert` | Succeeds |
| D | Composite key + `@ManyToOne` without converter | Succeeds |

### Spring Boot 3.5 / Hibernate 6.6

| Case | Mapping | Result |
|---|---|---|
| A | Composite `@Embeddable` key + `@ManyToOne` + `@Convert` | Succeeds |
| B | Composite `@Embeddable` key without converter (scalar key fields) | Succeeds |
| C | Simple primary key + `@Convert` | Succeeds |
| D | Composite key + `@ManyToOne` without converter | Succeeds |

## Root cause hypothesis

The failure is not caused by Hazelcast compact serialization support or by user entity serialization directly.

The failing object is Hibernate's cache key implementation:

- `org.hibernate.cache.internal.CacheKeyImplementation`

On the Hibernate 5 path, that cache key retains metadata that references:

- `org.hibernate.metamodel.model.convert.internal.JpaAttributeConverterImpl`

That converter metadata is not Java-serializable, so Hazelcast's JCache client cannot serialize the key when Hibernate consults the second-level cache.

The tests strongly suggest the risky combination is:

- Hibernate 5.x cache key implementation behavior
- composite embeddable identifier
- entity association inside the identifier
- attribute converter inside the identifier

Removing any one of the problematic key ingredients in the Hibernate 5 test matrix makes the issue disappear.

## Safe workaround for library users

The safest application-level workarounds are:

1. Do not cache affected entities.
   Disable L2 caching for entities whose embedded identifier combines `@ManyToOne` and `@Convert` on Hibernate 5.x.

2. Avoid `@Convert` inside embedded identifiers.
   Store a primitive or enum-safe scalar directly in the identifier instead of converter-backed metadata.

3. Replace `@ManyToOne` in composite keys with scalar foreign key fields.
   Use scalar key columns plus `@MapsId` or regular associations outside the identifier where possible.

4. Upgrade to the Hibernate 6 path if feasible.
   The same mapping is not failing in the current Spring Boot 3 / Hibernate 6 test matrix in this repository.

## Library-level fix evaluation

### Automatic mitigation

Not recommended.

`hazelcast-toolkit` does not control Hibernate's internal `CacheKeyImplementation` structure. Trying to rewrite or intercept Hibernate cache key serialization in the library would be invasive, version-sensitive, and high risk.

### Custom cache key strategy

Not recommended as a library default.

Hibernate extension points in this area are internal enough that a portable library-level override would likely become brittle across Hibernate versions.

### Fail fast

Possible, but only with significant introspection.

The library could inspect the JPA metamodel at startup and try to detect entities that have:

- second-level cache enabled
- embedded identifiers
- `@ManyToOne` inside the identifier
- `@Convert` inside the identifier

That is technically possible, but it adds non-trivial coupling to Hibernate metadata and risks false positives or version-specific breakage.

### Warn and document

Recommended.

The safest library-level action is:

- document the unsupported mapping on Hibernate 5.x style stacks
- optionally add a future startup warning in the Spring integration modules if a reliable metadata check is introduced

## Recommended toolkit action

For now, `hazelcast-toolkit` should:

1. Keep the regression tests in place.
2. Document the limitation for Hibernate 5 / Spring Boot 2 users.
3. Recommend mapping changes or cache opt-out for affected entities.
4. Avoid automatic hacks around Hibernate cache-key internals.

## Verification commands

```bash
./gradlew :toolkit-spring-boot2:test --tests io.github.javaquasar.hazelcast.toolkit.boot2.Boot2HibernateL2CacheKeyIssueIntegrationTest --offline
./gradlew :toolkit-spring-boot3:test --tests io.github.javaquasar.hazelcast.toolkit.boot3.Boot3HibernateL2CacheKeyIssueIntegrationTest --offline
```

On Windows PowerShell:

```powershell
.\gradlew :toolkit-spring-boot2:test --tests io.github.javaquasar.hazelcast.toolkit.boot2.Boot2HibernateL2CacheKeyIssueIntegrationTest --offline
.\gradlew :toolkit-spring-boot3:test --tests io.github.javaquasar.hazelcast.toolkit.boot3.Boot3HibernateL2CacheKeyIssueIntegrationTest --offline
```


