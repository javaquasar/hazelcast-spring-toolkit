package io.github.javaquasar.hazelcast.toolkit.example.boot3.service;

import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.NearCacheDemoResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ExampleBookNearCacheDiagnosticsService {

    private final EntityManagerFactory entityManagerFactory;
    private final ExampleBookCatalogService catalogService;

    public ExampleBookNearCacheDiagnosticsService(
            EntityManagerFactory entityManagerFactory,
            ExampleBookCatalogService catalogService) {
        this.entityManagerFactory = entityManagerFactory;
        this.catalogService = catalogService;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> cacheStats() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return Map.of(
                "l2HitCount", statistics.getSecondLevelCacheHitCount(),
                "l2MissCount", statistics.getSecondLevelCacheMissCount(),
                "l2PutCount", statistics.getSecondLevelCachePutCount()
        );
    }

    @Transactional(readOnly = true)
    public NearCacheDemoResponse nearCacheDemo(Long id) {
        ExampleBookEntity entity = catalogService.getBook(id);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        long coldReadNanos = timedFind(entity.getId());
        long hitsAfterColdRead = statistics.getSecondLevelCacheHitCount();

        long warmReadNanos = timedFind(entity.getId());
        long hitsAfterWarmRead = statistics.getSecondLevelCacheHitCount();

        entityManagerFactory.getCache().evict(ExampleBookEntity.class, entity.getId());
        long missesBeforeEvictedRead = statistics.getSecondLevelCacheMissCount();

        long postEvictionReadNanos = timedFind(entity.getId());
        long missesAfterEvictedRead = statistics.getSecondLevelCacheMissCount();

        return new NearCacheDemoResponse(
                entity.getId(),
                entity.getTitle(),
                new NearCacheDemoResponse.TimingsMs(
                        toMillis(coldReadNanos),
                        toMillis(warmReadNanos),
                        toMillis(postEvictionReadNanos)
                ),
                new NearCacheDemoResponse.HibernateL2Deltas(
                        hitsAfterWarmRead - hitsAfterColdRead,
                        missesAfterEvictedRead - missesBeforeEvictedRead
                ),
                new NearCacheDemoResponse.Interpretation(
                        warmReadNanos < coldReadNanos,
                        missesAfterEvictedRead > missesBeforeEvictedRead
                )
        );
    }

    private long timedFind(Long id) {
        long started = System.nanoTime();
        findInNewContext(id);
        return System.nanoTime() - started;
    }

    private ExampleBookEntity findInNewContext(Long id) {
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            ExampleBookEntity entity = entityManager.find(ExampleBookEntity.class, id);
            entityManager.getTransaction().commit();
            return entity;
        } catch (RuntimeException ex) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw ex;
        } finally {
            entityManager.close();
        }
    }

    private static long toMillis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }
}
