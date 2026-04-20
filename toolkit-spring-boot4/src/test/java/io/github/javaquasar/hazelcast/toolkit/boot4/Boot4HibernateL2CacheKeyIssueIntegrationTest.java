package io.github.javaquasar.hazelcast.toolkit.boot4;

import io.github.javaquasar.hazelcast.toolkit.boot4.l2.Boot4L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueSimpleConvertedEntity;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUser;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUserGroupManyToOneNoConverter;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUserGroupPkManyToOneNoConverter;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUserGroupPkScalarNoConverter;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUserGroupPkWithConverter;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUserGroupScalarNoConverter;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUserGroupType;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedIssueUserGroupWithConverter;
import io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue.SharedL2CacheKeyIssueTestApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying that Hibernate L2 cache key scenarios work correctly
 * under the Boot 4 auto-configuration stack with Hibernate 6.
 *
 * <p>Uses an in-process Hazelcast member (via {@link Boot4L2CacheTestConfiguration}) and an
 * H2 in-memory database — no Testcontainers required.
 *
 * <p>Covers: composite key with {@code @Convert}-annotated field, scalar composite key without
 * converter, {@code @ManyToOne} composite key, and simple entity with converted primary key.
 *
 * <p>Boot 3 exercises the same entity set against PostgreSQL via Testcontainers;
 * Boot 4 uses H2 as a deliberate infrastructure simplification — the Hibernate 6 L2 behaviour
 * under test is the same regardless of the underlying database.
 */
@SpringBootTest(
        classes = SharedL2CacheKeyIssueTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot4l2issue;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                "hazelcast.toolkit.hibernate.l2.use-statistics=true",
                "hazelcast.client.instance-name=boot4-l2-issue-test",
                "hazelcast.client.cluster-name=" + Boot4L2CacheTestConfiguration.CLUSTER_NAME,
                "hazelcast.client.network.smart-routing=false"
        }
)
@Import(Boot4L2CacheTestConfiguration.class)
class Boot4HibernateL2CacheKeyIssueIntegrationTest {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void resetStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void supportsCompositeKeyWithManyToOneAndConverterOnHibernate6() {
        Long userId = persistUser("user-a");
        transactionTemplate.executeWithoutResult(status -> {
            SharedIssueUser userRef = entityManager.getReference(SharedIssueUser.class, userId);
            entityManager.persist(new SharedIssueUserGroupWithConverter(
                    new SharedIssueUserGroupPkWithConverter(userRef, SharedIssueUserGroupType.REGULAR),
                    "with-converter"
            ));
        });

        SharedIssueUserGroupPkWithConverter cacheKey = transactionTemplate.execute(status ->
                new SharedIssueUserGroupPkWithConverter(
                        entityManager.getReference(SharedIssueUser.class, userId),
                        SharedIssueUserGroupType.REGULAR
                )
        );

        loadTwiceAndExpectL2Hit(SharedIssueUserGroupWithConverter.class, cacheKey);
    }

    @Test
    void succeedsForCompositeKeyWithoutConverter() {
        Long userId = persistUser("user-b");
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(
                new SharedIssueUserGroupScalarNoConverter(
                        new SharedIssueUserGroupPkScalarNoConverter(userId, SharedIssueUserGroupType.REGULAR),
                        "scalar-no-converter"
                )
        ));

        SharedIssueUserGroupPkScalarNoConverter cacheKey =
                new SharedIssueUserGroupPkScalarNoConverter(userId, SharedIssueUserGroupType.REGULAR);
        loadTwiceAndExpectL2Hit(SharedIssueUserGroupScalarNoConverter.class, cacheKey);
    }

    @Test
    void succeedsForSimplePrimaryKeyWithConverter() {
        Long id = transactionTemplate.execute(status -> {
            SharedIssueSimpleConvertedEntity entity =
                    new SharedIssueSimpleConvertedEntity(SharedIssueUserGroupType.VIP, "simple-converted");
            entityManager.persist(entity);
            return entity.getId();
        });

        loadTwiceAndExpectL2Hit(SharedIssueSimpleConvertedEntity.class, id);
    }

    @Test
    void succeedsForCompositeKeyWithManyToOneAndNoConverter() {
        Long userId = persistUser("user-c");
        transactionTemplate.executeWithoutResult(status -> {
            SharedIssueUser userRef = entityManager.getReference(SharedIssueUser.class, userId);
            entityManager.persist(new SharedIssueUserGroupManyToOneNoConverter(
                    new SharedIssueUserGroupPkManyToOneNoConverter(userRef, SharedIssueUserGroupType.VIP),
                    "many-to-one-no-converter"
            ));
        });

        SharedIssueUserGroupPkManyToOneNoConverter cacheKey = transactionTemplate.execute(status ->
                new SharedIssueUserGroupPkManyToOneNoConverter(
                        entityManager.getReference(SharedIssueUser.class, userId),
                        SharedIssueUserGroupType.VIP
                )
        );

        loadTwiceAndExpectL2Hit(SharedIssueUserGroupManyToOneNoConverter.class, cacheKey);
    }

    private Long persistUser(String username) {
        return transactionTemplate.execute(status -> {
            SharedIssueUser user = new SharedIssueUser(username);
            entityManager.persist(user);
            return user.getId();
        });
    }

    private <T> void loadTwiceAndExpectL2Hit(Class<T> entityClass, Object id) {
        T firstRead = transactionTemplate.execute(status -> entityManager.find(entityClass, id));
        assertNotNull(firstRead);
        long hitsBeforeSecondRead = statistics.getSecondLevelCacheHitCount();
        long putsBeforeSecondRead = statistics.getSecondLevelCachePutCount();

        T secondRead = transactionTemplate.execute(status -> entityManager.find(entityClass, id));
        assertNotNull(secondRead);
        assertTrue(statistics.getSecondLevelCachePutCount() >= putsBeforeSecondRead);
        assertTrue(statistics.getSecondLevelCacheHitCount() > hitsBeforeSecondRead,
                () -> "Expected a second-level cache hit for " + entityClass.getSimpleName());
    }
}
