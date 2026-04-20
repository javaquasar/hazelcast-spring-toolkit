package io.github.javaquasar.hazelcast.toolkit.example.boot3.config;

import io.github.javaquasar.hazelcast.toolkit.example.boot3.service.ExampleBookService;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.service.ExampleBookCatalogService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExampleDataInitializer implements ApplicationRunner {

    private final ExampleBookCatalogService catalogService;
    private final ExampleBookService bookService;

    public ExampleDataInitializer(ExampleBookCatalogService catalogService, ExampleBookService bookService) {
        this.catalogService = catalogService;
        this.bookService = bookService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (catalogService.countBooks() > 0) {
            return;
        }

        bookService.createBook(
                "Seeded Hazelcast Guide",
                "Toolkit Example",
                "978-0-00000-001-0",
                "Distributed Systems"
        );
    }
}
