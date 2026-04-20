package io.github.javaquasar.hazelcast.toolkit.boot4;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.EmbeddedHazelcastTestConfiguration;
import io.github.javaquasar.hazelcast.toolkit.spring.test.boot.ListenerTestConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test verifying that {@code @HzIMapListener} beans are auto-registered on the
 * correct {@link com.hazelcast.map.IMap} under the Boot 4 auto-configuration stack.
 *
 * <p>Uses an in-process Hazelcast member (via {@link EmbeddedHazelcastTestConfiguration}) so
 * no Testcontainers are required. JCache and JPA auto-configurations are excluded because this
 * test exercises only the listener-registration path.
 */
@SpringBootTest(
        classes = Boot4ListenerTestApplication.class,
        properties = {
                // Exclude persistence and JCache auto-configs — this test uses an embedded Hazelcast
                // member and does not need a JCache CacheManager.  Without the JCache exclusion,
                // HazelcastJCacheAutoConfiguration would attempt to bind the CacheManager to the
                // server instance via propertiesByInstanceItself, which only works for client instances.
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "io.github.javaquasar.hazelcast.toolkit.springboot4.config.HazelcastJCacheAutoConfiguration"
        }
)
@Import({EmbeddedHazelcastTestConfiguration.class, ListenerTestConfiguration.class})
class Boot4MapListenerIntegrationTest {

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
