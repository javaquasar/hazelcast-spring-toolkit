package io.github.javaquasar.hazelcast.toolkit.boot2;

import com.hazelcast.nio.serialization.HazelcastSerializationException;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2.Boot2L2CacheTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.Boot2L2CacheKeyIssueTestApplication;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUser;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUserGroupManyToOneNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUserGroupPkManyToOneNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUserGroupPkScalarNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUserGroupPkWithConverter;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUserGroupScalarNoConverter;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUserGroupType;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueUserGroupWithConverter;
import io.github.javaquasar.hazelcast.toolkit.boot2.l2issue.LegacyIssueSimpleConvertedEntity;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = Boot2L2CacheKeyIssueTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:boot2l2issue;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false",
                "hazelcast.toolkit.hibernate.l2.enabled=true",
                // extended-config=true: apply full JCache wiring so Hibernate serializes composite keys via L2 cache,
                // which is required to trigger the Hibernate 5 CacheKeyImplementation serialization failure.
                "hazelcast.toolkit.hibernate.l2.extended-config=true",
                // use-statistics=true: enable Hibernate cache statistics so getSecondLevelCacheHitCount() is tracked.
                "hazelcast.toolkit.hibernate.l2.use-statistics=true"
        }
)
@Import(Boot2L2CacheTestConfiguration.class)
class Boot2HibernateL2CacheKeyIssueIntegrationTest {

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
    void failsForCompositeKeyWithManyToOneAndConverterOnHibernate5() {
        Long userId = persistUser("legacy-a");

        // Hibernate 5 triggers cache-key serialization while checking transient state during persist.
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    LegacyIssueUser userRef = entityManager.getReference(LegacyIssueUser.class, userId);
                    entityManager.persist(new LegacyIssueUserGroupWithConverter(
                            new LegacyIssueUserGroupPkWithConverter(userRef, LegacyIssueUserGroupType.REGULAR),
                            "legacy-with-converter"
                    ));
                })
        );

        String chain = exceptionChain(thrown);
        assertTrue(chain.contains("cachekeyimplementation"), chain);
        assertTrue(
                chain.contains("jpaattributeconverterimpl")
                        || chain.contains("notserializableexception")
                        || chain.contains(HazelcastSerializationException.class.getName().toLowerCase(Locale.ROOT)),
                chain
        );
    }

    @Test
    void succeedsForCompositeKeyWithoutConverterOnHibernate5() {
        Long userId = persistUser("legacy-b");
        // Hibernate 5 + L2 cache: persist() throws PersistentObjectException for new entities with a
        // serializable @EmbeddedId because isTransient() returns null (L2 cache miss) and Hibernate 5
        // falls back to "non-null ID = detached". merge() is the correct workaround — it handles both
        // new and detached entities and does not suffer from this state-detection issue.
        transactionTemplate.executeWithoutResult(status -> entityManager.merge(
                new LegacyIssueUserGroupScalarNoConverter(
                        new LegacyIssueUserGroupPkScalarNoConverter(userId, LegacyIssueUserGroupType.REGULAR),
                        "legacy-scalar-no-converter"
                )
        ));

        LegacyIssueUserGroupPkScalarNoConverter cacheKey =
                new LegacyIssueUserGroupPkScalarNoConverter(userId, LegacyIssueUserGroupType.REGULAR);
        loadTwiceAndExpectL2Hit(LegacyIssueUserGroupScalarNoConverter.class, cacheKey);
    }

    @Test
    void succeedsForSimplePrimaryKeyWithConverterOnHibernate5() {
        Long id = transactionTemplate.execute(status -> {
            LegacyIssueSimpleConvertedEntity entity =
                    new LegacyIssueSimpleConvertedEntity(LegacyIssueUserGroupType.VIP, "legacy-simple-converted");
            entityManager.persist(entity);
            return entity.getId();
        });

        loadTwiceAndExpectL2Hit(LegacyIssueSimpleConvertedEntity.class, id);
    }

    @Test
    void succeedsForCompositeKeyWithManyToOneAndNoConverterOnHibernate5() {
        Long userId = persistUser("legacy-c");
        transactionTemplate.executeWithoutResult(status -> {
            LegacyIssueUser userRef = entityManager.getReference(LegacyIssueUser.class, userId);
            entityManager.persist(new LegacyIssueUserGroupManyToOneNoConverter(
                    new LegacyIssueUserGroupPkManyToOneNoConverter(userRef, LegacyIssueUserGroupType.VIP),
                    "legacy-no-converter"
            ));
        });

        LegacyIssueUserGroupPkManyToOneNoConverter cacheKey = transactionTemplate.execute(status ->
                new LegacyIssueUserGroupPkManyToOneNoConverter(
                        entityManager.getReference(LegacyIssueUser.class, userId),
                        LegacyIssueUserGroupType.VIP
                )
        );

        loadTwiceAndExpectL2Hit(LegacyIssueUserGroupManyToOneNoConverter.class, cacheKey);
    }

    private <T> void loadTwiceAndExpectL2Hit(Class<T> entityClass, Object id) {
        T firstRead = transactionTemplate.execute(status -> entityManager.find(entityClass, id));
        assertNotNull(firstRead);
        long hitsBeforeSecondRead = statistics.getSecondLevelCacheHitCount();

        T secondRead = transactionTemplate.execute(status -> entityManager.find(entityClass, id));
        assertNotNull(secondRead);
        assertTrue(statistics.getSecondLevelCacheHitCount() > hitsBeforeSecondRead,
                () -> "Expected a second-level cache hit for " + entityClass.getSimpleName());
    }

    private Long persistUser(String username) {
        return transactionTemplate.execute(status -> {
            LegacyIssueUser user = new LegacyIssueUser(username);
            entityManager.persist(user);
            return user.getId();
        });
    }

    private String exceptionChain(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            builder.append(current.getClass().getName().toLowerCase(Locale.ROOT));
            builder.append(':');
            if (current.getMessage() != null) {
                builder.append(current.getMessage().toLowerCase(Locale.ROOT));
            }
            builder.append('\n');
            current = current.getCause();
        }
        return builder.toString();
    }
}




