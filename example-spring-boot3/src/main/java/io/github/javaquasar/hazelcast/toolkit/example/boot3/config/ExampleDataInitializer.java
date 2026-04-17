package io.github.javaquasar.hazelcast.toolkit.example.boot3.config;

import io.github.javaquasar.hazelcast.toolkit.example.boot3.repository.ExampleBookRepository;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.service.ExampleBookService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExampleDataInitializer implements ApplicationRunner {

    private final ExampleBookRepository repository;
    private final ExampleBookService bookService;

    public ExampleDataInitializer(ExampleBookRepository repository, ExampleBookService bookService) {
        this.repository = repository;
        this.bookService = bookService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
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
