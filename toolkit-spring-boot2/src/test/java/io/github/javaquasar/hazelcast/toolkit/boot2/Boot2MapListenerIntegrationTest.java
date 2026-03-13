package io.github.javaquasar.hazelcast.toolkit.boot2;

import com.hazelcast.core.HazelcastInstance;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        classes = Boot2ListenerTestApplication.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
        }
)
@Import({EmbeddedHazelcastTestConfiguration.class, ListenerTestConfiguration.class})
class Boot2MapListenerIntegrationTest {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Test
    void registersHazelcastMapListenerAndReceivesEntryAddedEvent() {
        assertNotNull(hazelcastInstance);
        ListenerTestConfiguration.RecordingEntryListener.reset();

        hazelcastInstance
                .getMap(ListenerTestConfiguration.RecordingEntryListener.MAP_NAME)
                .put("listener-key", "listener-value");

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(1, ListenerTestConfiguration.RecordingEntryListener.addedEvents()));
    }
}
