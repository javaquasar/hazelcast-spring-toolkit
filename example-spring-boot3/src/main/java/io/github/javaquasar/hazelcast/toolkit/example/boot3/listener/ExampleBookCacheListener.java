package io.github.javaquasar.hazelcast.toolkit.example.boot3.listener;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzIMapListener;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.service.ExampleBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

@Component
@HzIMapListener(map = ExampleBookService.BOOKS_MAP)
public class ExampleBookCacheListener implements
        EntryAddedListener<String, ExampleBookCacheEntry>,
        EntryUpdatedListener<String, ExampleBookCacheEntry>,
        EntryRemovedListener<String, ExampleBookCacheEntry> {

    private static final Logger log = LoggerFactory.getLogger(ExampleBookCacheListener.class);
    private final AtomicInteger addedEvents = new AtomicInteger();
    private final AtomicInteger updatedEvents = new AtomicInteger();
    private final AtomicInteger removedEvents = new AtomicInteger();

    @Override
    public void entryAdded(EntryEvent<String, ExampleBookCacheEntry> event) {
        addedEvents.incrementAndGet();
        log.info("Hazelcast map listener received book cache entry: key={}, value={}", event.getKey(), event.getValue());
    }

    @Override
    public void entryUpdated(EntryEvent<String, ExampleBookCacheEntry> event) {
        updatedEvents.incrementAndGet();
        log.info("Hazelcast map listener updated book cache entry: key={}, value={}", event.getKey(), event.getValue());
    }

    @Override
    public void entryRemoved(EntryEvent<String, ExampleBookCacheEntry> event) {
        removedEvents.incrementAndGet();
        log.info("Hazelcast map listener removed book cache entry: key={}", event.getKey());
    }

    public void reset() {
        addedEvents.set(0);
        updatedEvents.set(0);
        removedEvents.set(0);
    }

    public int addedEvents() {
        return addedEvents.get();
    }

    public int updatedEvents() {
        return updatedEvents.get();
    }

    public int removedEvents() {
        return removedEvents.get();
    }
}
