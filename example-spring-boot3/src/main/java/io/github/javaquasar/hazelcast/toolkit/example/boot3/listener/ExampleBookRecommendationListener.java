package io.github.javaquasar.hazelcast.toolkit.example.boot3.listener;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzIMapListener;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.service.ExampleBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@HzIMapListener(map = ExampleBookService.RECOMMENDATIONS_MAP)
public class ExampleBookRecommendationListener implements
        EntryAddedListener<String, ExampleBookRecommendation>,
        EntryUpdatedListener<String, ExampleBookRecommendation>,
        EntryRemovedListener<String, ExampleBookRecommendation> {

    private static final Logger log = LoggerFactory.getLogger(ExampleBookRecommendationListener.class);

    private final AtomicInteger addedEvents = new AtomicInteger();
    private final AtomicInteger updatedEvents = new AtomicInteger();
    private final AtomicInteger removedEvents = new AtomicInteger();

    @Override
    public void entryAdded(EntryEvent<String, ExampleBookRecommendation> event) {
        addedEvents.incrementAndGet();
        log.info("Hazelcast recommendation listener received entry: key={}, value={}", event.getKey(), event.getValue());
    }

    @Override
    public void entryUpdated(EntryEvent<String, ExampleBookRecommendation> event) {
        updatedEvents.incrementAndGet();
        log.info("Hazelcast recommendation listener updated entry: key={}, value={}", event.getKey(), event.getValue());
    }

    @Override
    public void entryRemoved(EntryEvent<String, ExampleBookRecommendation> event) {
        removedEvents.incrementAndGet();
        log.info("Hazelcast recommendation listener removed entry: key={}", event.getKey());
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
