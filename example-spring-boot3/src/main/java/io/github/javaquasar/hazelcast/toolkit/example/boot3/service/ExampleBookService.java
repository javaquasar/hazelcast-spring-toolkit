package io.github.javaquasar.hazelcast.toolkit.example.boot3.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.repository.ExampleBookRepository;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.ExampleBookNotFoundException;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.NearCacheDemoResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ExampleBookService {

    public static final String BOOKS_MAP = "example-books";
    public static final String RECOMMENDATIONS_MAP = "example-book-recommendations";

    private final ExampleBookRepository repository;
    private final HazelcastInstance hazelcastInstance;
    private final EntityManagerFactory entityManagerFactory;

    public ExampleBookService(
            ExampleBookRepository repository,
            HazelcastInstance hazelcastInstance,
            EntityManagerFactory entityManagerFactory) {
        this.repository = repository;
        this.hazelcastInstance = hazelcastInstance;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Transactional
    public ExampleBookEntity createBook(String title, String author, String isbn, String genre) {
        ExampleBookEntity entity = repository.save(new ExampleBookEntity(title, author, isbn, genre));
        syncHazelcastViews(entity);
        return entity;
    }

    @Transactional
    public ExampleBookEntity updateBook(Long id, String title, String author, String genre) {
        ExampleBookEntity entity = requireBook(id);
        entity.setTitle(title);
        entity.setAuthor(author);
        entity.setGenre(genre);
        ExampleBookEntity saved = repository.save(entity);
        syncHazelcastViews(saved);
        return saved;
    }

    @Transactional
    public void deleteBook(Long id) {
        ExampleBookEntity entity = requireBook(id);
        repository.delete(entity);
        entityManagerFactory.getCache().evict(ExampleBookEntity.class, id);
        booksMap().remove(id.toString());
        recommendationsMap().remove(id.toString());
    }

    @Transactional(readOnly = true)
    public ExampleBookEntity getBook(Long id) {
        return requireBook(id);
    }

    @Transactional(readOnly = true)
    public ExampleBookCacheEntry getCacheEntry(Long id) {
        return booksMap().get(id.toString());
    }

    @Transactional(readOnly = true)
    public ExampleBookRecommendation getRecommendation(Long id) {
        return recommendationsMap().get(id.toString());
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
        ExampleBookEntity entity = requireBook(id);
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

    private ExampleBookCacheEntry toCacheEntry(ExampleBookEntity entity) {
        return new ExampleBookCacheEntry(
                entity.getId().toString(),
                entity.getTitle(),
                entity.getAuthor(),
                entity.getIsbn(),
                entity.getGenre(),
                new ExampleBookCacheEntry.PublisherInfo(
                        "Hazelcast Press",
                        "Valletta"
                ),
                new ExampleBookCacheEntry.InventorySnapshot(
                        "shelf-" + entity.getGenre().toLowerCase().replace(' ', '-'),
                        12,
                        true,
                        new String[]{"hazelcast", "spring", entity.getGenre().toLowerCase().replace(' ', '-')}
                )
        );
    }

    private ExampleBookRecommendation toRecommendation(ExampleBookEntity entity) {
        return new ExampleBookRecommendation(
                entity.getId().toString(),
                "Readers interested in " + entity.getGenre() + " usually revisit this title",
                92,
                "reflective-compact-demo",
                new String[]{entity.getGenre(), "Cloud Native", "Caching"}
        );
    }

    private void syncHazelcastViews(ExampleBookEntity entity) {
        booksMap().put(entity.getId().toString(), toCacheEntry(entity));
        recommendationsMap().put(entity.getId().toString(), toRecommendation(entity));
    }

    private IMap<String, ExampleBookCacheEntry> booksMap() {
        return hazelcastInstance.getMap(BOOKS_MAP);
    }

    private IMap<String, ExampleBookRecommendation> recommendationsMap() {
        return hazelcastInstance.getMap(RECOMMENDATIONS_MAP);
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

    private ExampleBookEntity requireBook(Long id) {
        return repository.findById(id).orElseThrow(() -> new ExampleBookNotFoundException(id));
    }
}
