package io.github.javaquasar.hazelcast.toolkit.example.boot3.service;

import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.NearCacheDemoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ExampleBookService {

    public static final String BOOKS_MAP = ExampleBookProjectionService.BOOKS_MAP;
    public static final String RECOMMENDATIONS_MAP = ExampleBookProjectionService.RECOMMENDATIONS_MAP;

    private final ExampleBookCatalogService catalogService;
    private final ExampleBookProjectionService projectionService;
    private final ExampleBookNearCacheDiagnosticsService nearCacheDiagnosticsService;

    public ExampleBookService(
            ExampleBookCatalogService catalogService,
            ExampleBookProjectionService projectionService,
            ExampleBookNearCacheDiagnosticsService nearCacheDiagnosticsService) {
        this.catalogService = catalogService;
        this.projectionService = projectionService;
        this.nearCacheDiagnosticsService = nearCacheDiagnosticsService;
    }

    @Transactional
    public ExampleBookEntity createBook(String title, String author, String isbn, String genre) {
        ExampleBookEntity entity = catalogService.createBook(title, author, isbn, genre);
        projectionService.syncHazelcastViews(entity);
        return entity;
    }

    @Transactional
    public ExampleBookEntity updateBook(Long id, String title, String author, String genre) {
        ExampleBookEntity saved = catalogService.updateBook(id, title, author, genre);
        projectionService.syncHazelcastViews(saved);
        return saved;
    }

    @Transactional
    public void deleteBook(Long id) {
        catalogService.deleteBook(id);
        projectionService.removeHazelcastViews(id);
    }

    @Transactional(readOnly = true)
    public ExampleBookEntity getBook(Long id) {
        return catalogService.getBook(id);
    }

    @Transactional(readOnly = true)
    public ExampleBookCacheEntry getCacheEntry(Long id) {
        return projectionService.getCacheEntry(id);
    }

    @Transactional(readOnly = true)
    public ExampleBookRecommendation getRecommendation(Long id) {
        return projectionService.getRecommendation(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> cacheStats() {
        return nearCacheDiagnosticsService.cacheStats();
    }

    @Transactional(readOnly = true)
    public NearCacheDemoResponse nearCacheDemo(Long id) {
        return nearCacheDiagnosticsService.nearCacheDemo(id);
    }
}
