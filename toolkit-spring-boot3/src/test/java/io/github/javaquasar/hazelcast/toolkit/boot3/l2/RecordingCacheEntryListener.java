package io.github.javaquasar.hazelcast.toolkit.boot3.l2;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryUpdatedListener;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordingCacheEntryListener implements CacheEntryCreatedListener<Object, Object>, CacheEntryUpdatedListener<Object, Object>, Serializable {

    private static final AtomicInteger CREATED_EVENTS = new AtomicInteger();
    private static final AtomicInteger UPDATED_EVENTS = new AtomicInteger();

    @Override
    public void onCreated(Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) throws CacheEntryListenerException {
        events.forEach(event -> CREATED_EVENTS.incrementAndGet());
    }

    @Override
    public void onUpdated(Iterable<CacheEntryEvent<? extends Object, ? extends Object>> events) throws CacheEntryListenerException {
        events.forEach(event -> UPDATED_EVENTS.incrementAndGet());
    }

    public static void reset() {
        CREATED_EVENTS.set(0);
        UPDATED_EVENTS.set(0);
    }

    public static int createdEvents() {
        return CREATED_EVENTS.get();
    }

    public static int updatedEvents() {
        return UPDATED_EVENTS.get();
    }

    public static int totalEvents() {
        return createdEvents() + updatedEvents();
    }
}
