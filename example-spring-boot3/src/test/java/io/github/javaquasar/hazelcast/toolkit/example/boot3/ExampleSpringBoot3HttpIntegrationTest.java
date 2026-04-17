package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookCacheEntry;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookRecommendation;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.CreateBookRequest;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.NearCacheDemoResponse;
import io.github.javaquasar.hazelcast.toolkit.example.boot3.web.UpdateBookRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ExampleSpringBoot3Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.banner-mode=off"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExampleSpringBoot3HttpIntegrationTest {

    private static HazelcastInstance testMember;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startHazelcast() {
        Config config = new Config();
        config.setClusterName("dev");
        config.setProperty("hazelcast.logging.type", "slf4j");
        config.setProperty("hazelcast.shutdownhook.enabled", "false");

        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(5701);
        networkConfig.setPortAutoIncrement(false);

        testMember = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void shutdownHazelcast() {
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
        testMember = null;
    }

    @Test
    void runsDemoHttpFlowAgainstRealHttpServer() {
        ExampleBookEntity created = exchange(
                HttpMethod.POST,
                "/api/books",
                new CreateBookRequest(
                        "HTTP Flow Book",
                        "Integration Tester",
                        "978-1-77777-" + System.nanoTime(),
                        "Platform Engineering"
                ),
                ExampleBookEntity.class
        ).getBody();

        assertThat(created).isNotNull();
        Long id = created.getId();

        ExampleBookEntity firstRead = exchange(HttpMethod.GET, "/api/books/" + id, null, ExampleBookEntity.class).getBody();
        ExampleBookEntity secondRead = exchange(HttpMethod.GET, "/api/books/" + id, null, ExampleBookEntity.class).getBody();
        Map<String, Long> stats = exchange(HttpMethod.GET, "/api/books/stats", null, new ParameterizedTypeReference<Map<String, Long>>() {
        }).getBody();
        ExampleBookCacheEntry cacheEntry = exchange(HttpMethod.GET, "/api/books/cache/" + id, null, ExampleBookCacheEntry.class).getBody();
        ExampleBookRecommendation recommendation = exchange(
                HttpMethod.GET,
                "/api/books/recommendations/" + id,
                null,
                ExampleBookRecommendation.class
        ).getBody();

        ExampleBookEntity updated = exchange(
                HttpMethod.PUT,
                "/api/books/" + id,
                new UpdateBookRequest("HTTP Flow Book Revised", "Integration Tester", "Caching Strategy"),
                ExampleBookEntity.class
        ).getBody();

        NearCacheDemoResponse nearCacheDemo = exchange(
                HttpMethod.GET,
                "/api/books/" + id + "/near-cache-demo",
                null,
                NearCacheDemoResponse.class
        ).getBody();

        Map<String, Object> actuatorResponse = exchange(
                HttpMethod.GET,
                "/actuator/hazelcastNearCache?entity=io.github.javaquasar.hazelcast.toolkit.example.boot3.model.ExampleBookEntity&id=" + id,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        ).getBody();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl("/api/books/" + id),
                HttpMethod.DELETE,
                null,
                Void.class
        );

        ResponseEntity<String> missingAfterDelete = restTemplate.getForEntity(baseUrl("/api/books/" + id), String.class);

        assertThat(firstRead).isNotNull();
        assertThat(secondRead).isNotNull();
        assertThat(stats).isNotNull();
        assertThat(stats.get("l2HitCount")).isGreaterThan(0L);
        assertThat(cacheEntry).isNotNull();
        assertThat(cacheEntry.getPublisher().getName()).isEqualTo("Hazelcast Press");
        assertThat(recommendation).isNotNull();
        assertThat(recommendation.getRecommendedBy()).isEqualTo("reflective-compact-demo");
        assertThat(updated).isNotNull();
        assertThat(updated.getGenre()).isEqualTo("Caching Strategy");
        assertThat(nearCacheDemo).isNotNull();
        assertThat(nearCacheDemo.interpretation().evictionForcedMiss()).isTrue();
        assertThat(actuatorResponse).isNotNull();
        assertThat(actuatorResponse.get("status")).isEqualTo("OK");
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(204));
        assertThat(missingAfterDelete.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(404));
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body, Class<T> responseType) {
        return restTemplate.exchange(
                baseUrl(path),
                method,
                body == null ? null : new HttpEntity<>(body),
                responseType
        );
    }

    private <T> ResponseEntity<T> exchange(
            HttpMethod method,
            String path,
            Object body,
            ParameterizedTypeReference<T> responseType
    ) {
        return restTemplate.exchange(
                baseUrl(path),
                method,
                body == null ? null : new HttpEntity<>(body),
                responseType
        );
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
