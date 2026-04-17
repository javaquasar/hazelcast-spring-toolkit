package io.github.javaquasar.hazelcast.toolkit.example.boot3.web;

import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.service.ExampleBookService;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.NearCacheDemoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class ExampleBookController {

    private final ExampleBookService bookService;

    public ExampleBookController(ExampleBookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public ExampleBookEntity create(@RequestBody CreateBookRequest request) {
        return bookService.createBook(request.title(), request.author(), request.isbn(), request.genre());
    }

    @GetMapping("/{id}")
    public ExampleBookEntity get(@PathVariable("id") Long id) {
        return bookService.getBook(id);
    }

    @PutMapping("/{id}")
    public ExampleBookEntity update(@PathVariable("id") Long id, @RequestBody UpdateBookRequest request) {
        return bookService.updateBook(id, request.title(), request.author(), request.genre());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cache/{id}")
    public ExampleBookCacheEntry getCacheEntry(@PathVariable("id") Long id) {
        return bookService.getCacheEntry(id);
    }

    @GetMapping("/recommendations/{id}")
    public ExampleBookRecommendation getRecommendation(@PathVariable("id") Long id) {
        return bookService.getRecommendation(id);
    }

    @GetMapping("/{id}/near-cache-demo")
    public NearCacheDemoResponse nearCacheDemo(@PathVariable("id") Long id) {
        return bookService.nearCacheDemo(id);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return bookService.cacheStats();
    }
}
