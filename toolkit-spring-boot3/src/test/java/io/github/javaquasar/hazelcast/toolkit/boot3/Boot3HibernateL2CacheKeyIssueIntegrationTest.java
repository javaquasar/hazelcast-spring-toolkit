package io.github.javaquasar.hazelcast.toolkit.boot3;

import io.github.javaquasar.hazelcast.toolkit.boot3.l2.L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.Boot3L2CacheKeyIssueTestApplication;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUser;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUserGroupManyToOneNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUserGroupPkManyToOneNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUserGroupPkScalarNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUserGroupPkWithConverter;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUserGroupScalarNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUserGroupType;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueUserGroupWithConverter;
import io.github.javaquasar.hazelcast.toolkit.boot3.l2issue.IssueSimpleConvertedEntity;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = Boot3L2CacheKeyIssueTestApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false"
        }
)
@Import(L2CacheTestConfiguration.class)
class Boot3HibernateL2CacheKeyIssueIntegrationTest extends TestcontainersEnvironment {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

    @BeforeEach
    void resetStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void supportsCompositeKeyWithManyToOneAndConverterOnHibernate6() {
        // The same mapping that fails on Hibernate 5 stays cacheable on the current Boot 3 stack.
        Long userId = persistUser("user-a");
        transactionTemplate.executeWithoutResult(status -> {
            IssueUser userRef = entityManager.getReference(IssueUser.class, userId);
            entityManager.persist(new IssueUserGroupWithConverter(
                    new IssueUserGroupPkWithConverter(userRef, IssueUserGroupType.REGULAR),
                    "with-converter"
            ));
        });

        IssueUserGroupPkWithConverter cacheKey = transactionTemplate.execute(status ->
                new IssueUserGroupPkWithConverter(
                        entityManager.getReference(IssueUser.class, userId),
                        IssueUserGroupType.REGULAR
                )
        );

        loadTwiceAndExpectL2Hit(IssueUserGroupWithConverter.class, cacheKey);
    }

    @Test
    void succeedsForCompositeKeyWithoutConverter() {
        Long userId = persistUser("user-b");
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(
                new IssueUserGroupScalarNoConverter(
                        new IssueUserGroupPkScalarNoConverter(userId, IssueUserGroupType.REGULAR),
                        "scalar-no-converter"
                )
        ));

        IssueUserGroupPkScalarNoConverter cacheKey = new IssueUserGroupPkScalarNoConverter(userId, IssueUserGroupType.REGULAR);
        loadTwiceAndExpectL2Hit(IssueUserGroupScalarNoConverter.class, cacheKey);
    }

    @Test
    void succeedsForSimplePrimaryKeyWithConverter() {
        Long id = transactionTemplate.execute(status -> {
            IssueSimpleConvertedEntity entity = new IssueSimpleConvertedEntity(IssueUserGroupType.VIP, "simple-converted");
            entityManager.persist(entity);
            return entity.getId();
        });

        loadTwiceAndExpectL2Hit(IssueSimpleConvertedEntity.class, id);
    }

    @Test
    void succeedsForCompositeKeyWithManyToOneAndNoConverter() {
        Long userId = persistUser("user-c");
        transactionTemplate.executeWithoutResult(status -> {
            IssueUser userRef = entityManager.getReference(IssueUser.class, userId);
            entityManager.persist(new IssueUserGroupManyToOneNoConverter(
                    new IssueUserGroupPkManyToOneNoConverter(userRef, IssueUserGroupType.VIP),
                    "many-to-one-no-converter"
            ));
        });

        IssueUserGroupPkManyToOneNoConverter cacheKey = transactionTemplate.execute(status ->
                new IssueUserGroupPkManyToOneNoConverter(
                        entityManager.getReference(IssueUser.class, userId),
                        IssueUserGroupType.VIP
                )
        );

        loadTwiceAndExpectL2Hit(IssueUserGroupManyToOneNoConverter.class, cacheKey);
    }

    private Long persistUser(String username) {
        return transactionTemplate.execute(status -> {
            IssueUser user = new IssueUser(username);
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




