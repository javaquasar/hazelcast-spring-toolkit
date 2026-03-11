package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzIMapListener;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicInteger;

@TestConfiguration
public class ListenerTestConfiguration {

    @Bean
    public RecordingEntryListener recordingEntryListener() {
        RecordingEntryListener.reset();
        return new RecordingEntryListener();
    }

    @HzIMapListener(map = RecordingEntryListener.MAP_NAME)
    public static class RecordingEntryListener implements EntryAddedListener<String, String> {

        public static final String MAP_NAME = "boot3-listener-test-map";
        private static final AtomicInteger ADDED_EVENTS = new AtomicInteger();

        public static void reset() {
            ADDED_EVENTS.set(0);
        }

        public static int addedEvents() {
            return ADDED_EVENTS.get();
        }

        @Override
        public void entryAdded(EntryEvent<String, String> event) {
            ADDED_EVENTS.incrementAndGet();
        }
    }
}
