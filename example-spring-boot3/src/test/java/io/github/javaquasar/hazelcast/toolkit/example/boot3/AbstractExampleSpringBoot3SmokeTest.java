package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.listener.ExampleBookCacheListener;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.listener.ExampleBookRecommendationListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ExampleSpringBoot3Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = AbstractExampleSpringBoot3SmokeTest.EmbeddedHazelcastInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractExampleSpringBoot3SmokeTest {

    private static volatile HazelcastInstance testMember;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExampleBookCacheListener cacheListener;

    @Autowired
    private ExampleBookRecommendationListener recommendationListener;

    @AfterAll
    static void shutdownHazelcast() {
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
        testMember = null;
    }

    private static synchronized void ensureMemberRunning() {
        if (testMember != null && testMember.getLifecycleService().isRunning()) {
            return;
        }

        Config config = new Config();
        config.setClusterName("dev");
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.setProperty("hazelcast.shutdownhook.enabled", "false");

        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(5701);
        networkConfig.setPortAutoIncrement(false);

        testMember = Hazelcast.newHazelcastInstance(config);
    }

    @BeforeEach
    void resetListener() {
        cacheListener.reset();
        recommendationListener.reset();
    }

    @Test
    void createsBookReadsItFromL2AndWritesCompactEntryToHazelcastMap() throws Exception {
        String isbn = "978-1-23456-" + System.nanoTime();

        String responseBody = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Hazelcast in Action",
                                  "author": "Example Author",
                                  "isbn": "%s",
                                  "genre": "Distributed Systems"
                                }
                                """.formatted(isbn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Hazelcast in Action"))
                .andExpect(jsonPath("$.author").value("Example Author"))
                .andExpect(jsonPath("$.isbn").value(isbn))
                .andExpect(jsonPath("$.genre").value("Distributed Systems"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdBook = objectMapper.readTree(responseBody);
        long id = createdBook.path("id").asLong();

        awaitListenerEvent();

        mockMvc.perform(get("/api/books/cache/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.toString(id)))
                .andExpect(jsonPath("$.title").value("Hazelcast in Action"))
                .andExpect(jsonPath("$.author").value("Example Author"))
                .andExpect(jsonPath("$.isbn").value(isbn))
                .andExpect(jsonPath("$.genre").value("Distributed Systems"))
                .andExpect(jsonPath("$.publisher.name").value("Hazelcast Press"))
                .andExpect(jsonPath("$.publisher.city").value("Valletta"))
                .andExpect(jsonPath("$.inventory.shelfCode").value("shelf-distributed-systems"))
                .andExpect(jsonPath("$.inventory.copiesAvailable").value(12))
                .andExpect(jsonPath("$.inventory.featured").value(true))
                .andExpect(jsonPath("$.inventory.tags[0]").value("hazelcast"))
                .andExpect(jsonPath("$.inventory.tags[1]").value("spring"))
                .andExpect(jsonPath("$.inventory.tags[2]").value("distributed-systems"));

        mockMvc.perform(get("/api/books/recommendations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(Long.toString(id)))
                .andExpect(jsonPath("$.recommendedBy").value("reflective-compact-demo"))
                .andExpect(jsonPath("$.relatedGenres[0]").value("Distributed Systems"));

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        String statsResponse = mockMvc.perform(get("/api/books/stats"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode stats = objectMapper.readTree(statsResponse);
        assertThat(stats.path("l2PutCount").asLong()).isGreaterThan(0L);
        assertThat(stats.path("l2HitCount").asLong()).isGreaterThan(0L);

        mockMvc.perform(put("/api/books/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Hazelcast in Action Revised",
                                  "author": "Example Author",
                                  "genre": "Cloud Architecture"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hazelcast in Action Revised"))
                .andExpect(jsonPath("$.genre").value("Cloud Architecture"));

        awaitUpdatedListenerEvent();

        mockMvc.perform(get("/api/books/cache/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hazelcast in Action Revised"))
                .andExpect(jsonPath("$.genre").value("Cloud Architecture"))
                .andExpect(jsonPath("$.inventory.shelfCode").value("shelf-cloud-architecture"));

        mockMvc.perform(get("/api/books/recommendations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedGenres[0]").value("Cloud Architecture"));

        mockMvc.perform(get("/api/books/{id}/near-cache-demo", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").value(id))
                .andExpect(jsonPath("$.interpretation.warmReadFasterThanColdRead").value(true))
                .andExpect(jsonPath("$.interpretation.evictionForcedMiss").value(true));

        mockMvc.perform(get("/actuator/hazelcastNearCache")
                        .param("entity", "io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity")
                        .param("id", Long.toString(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(delete("/api/books/{id}", id))
                .andExpect(status().isNoContent());

        awaitRemovedListenerEvent();

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isNotFound());

        assertThat(cacheListener.addedEvents()).isGreaterThan(0);
        assertThat(cacheListener.updatedEvents()).isGreaterThan(0);
        assertThat(cacheListener.removedEvents()).isGreaterThan(0);
        assertThat(recommendationListener.addedEvents()).isGreaterThan(0);
        assertThat(recommendationListener.updatedEvents()).isGreaterThan(0);
        assertThat(recommendationListener.removedEvents()).isGreaterThan(0);
    }

    private void awaitListenerEvent() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (cacheListener.addedEvents() > 0) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for @HzIMapListener to receive the Hazelcast map event");
    }

    private void awaitUpdatedListenerEvent() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (cacheListener.updatedEvents() > 0) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for @HzIMapListener to receive the Hazelcast map update event");
    }

    private void awaitRemovedListenerEvent() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (cacheListener.removedEvents() > 0) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for @HzIMapListener to receive the Hazelcast map remove event");
    }

    static class EmbeddedHazelcastInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            ensureMemberRunning();
        }
    }
}
