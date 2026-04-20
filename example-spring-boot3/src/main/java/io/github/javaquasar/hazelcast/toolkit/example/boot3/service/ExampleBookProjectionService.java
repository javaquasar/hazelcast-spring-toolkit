package io.github.javaquasar.hazelcast.toolkit.example.boot3.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import org.springframework.stereotype.Service;

@Service
public class ExampleBookProjectionService {

    public static final String BOOKS_MAP = "example-books";
    public static final String RECOMMENDATIONS_MAP = "example-book-recommendations";

    private final HazelcastInstance hazelcastInstance;

    public ExampleBookProjectionService(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    public void syncHazelcastViews(ExampleBookEntity entity) {
        booksMap().put(entity.getId().toString(), toCacheEntry(entity));
        recommendationsMap().put(entity.getId().toString(), toRecommendation(entity));
    }

    public void removeHazelcastViews(Long id) {
        booksMap().remove(id.toString());
        recommendationsMap().remove(id.toString());
    }

    public ExampleBookCacheEntry getCacheEntry(Long id) {
        return booksMap().get(id.toString());
    }

    public ExampleBookRecommendation getRecommendation(Long id) {
        return recommendationsMap().get(id.toString());
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

    private IMap<String, ExampleBookCacheEntry> booksMap() {
        return hazelcastInstance.getMap(BOOKS_MAP);
    }

    private IMap<String, ExampleBookRecommendation> recommendationsMap() {
        return hazelcastInstance.getMap(RECOMMENDATIONS_MAP);
    }
}
