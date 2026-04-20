package io.github.javaquasar.hazelcast.toolkit.example.boot3.service;

import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.repository.ExampleBookRepository;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.ExampleBookNotFoundException;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExampleBookCatalogService {

    private final ExampleBookRepository repository;
    private final EntityManagerFactory entityManagerFactory;

    public ExampleBookCatalogService(ExampleBookRepository repository, EntityManagerFactory entityManagerFactory) {
        this.repository = repository;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Transactional
    public ExampleBookEntity createBook(String title, String author, String isbn, String genre) {
        return repository.save(new ExampleBookEntity(title, author, isbn, genre));
    }

    @Transactional
    public ExampleBookEntity updateBook(Long id, String title, String author, String genre) {
        ExampleBookEntity entity = requireBook(id);
        entity.setTitle(title);
        entity.setAuthor(author);
        entity.setGenre(genre);
        return repository.save(entity);
    }

    @Transactional
    public void deleteBook(Long id) {
        ExampleBookEntity entity = requireBook(id);
        repository.delete(entity);
        entityManagerFactory.getCache().evict(ExampleBookEntity.class, id);
    }

    @Transactional(readOnly = true)
    public ExampleBookEntity getBook(Long id) {
        return requireBook(id);
    }

    @Transactional(readOnly = true)
    public long countBooks() {
        return repository.count();
    }

    private ExampleBookEntity requireBook(Long id) {
        return repository.findById(id).orElseThrow(() -> new ExampleBookNotFoundException(id));
    }
}
