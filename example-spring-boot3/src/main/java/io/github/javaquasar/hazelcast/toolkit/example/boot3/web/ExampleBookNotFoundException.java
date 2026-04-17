package io.github.javaquasar.hazelcast.toolkit.example.boot3.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ExampleBookNotFoundException extends RuntimeException {

    public ExampleBookNotFoundException(Long id) {
        super("Book not found: " + id);
    }
}
