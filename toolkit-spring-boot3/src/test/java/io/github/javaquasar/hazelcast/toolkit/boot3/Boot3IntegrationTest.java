package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = TestApplication.class)
class Boot3IntegrationTest {

    private static final String HAZELCAST_CLUSTER_NAME = "core";
    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withNetwork(NETWORK)
            .withDatabaseName("demo")
            .withUsername("demo")
            .withPassword("demo");

    @Container
    static final GenericContainer<?> HAZELCAST_1 = new GenericContainer<>(DockerImageName.parse("hazelcast/hazelcast:5.5.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("hz1")
            .withEnv("HZ_CLUSTERNAME", HAZELCAST_CLUSTER_NAME)
            .withExposedPorts(5701);

    @Container
    static final GenericContainer<?> HAZELCAST_2 = new GenericContainer<>(DockerImageName.parse("hazelcast/hazelcast:5.5.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("hz2")
            .withEnv("HZ_CLUSTERNAME", HAZELCAST_CLUSTER_NAME)
            .withExposedPorts(5701);

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("hazelcast.client.cluster-name", () -> HAZELCAST_CLUSTER_NAME);
        registry.add("hazelcast.client.network.cluster-members", () -> List.of(
                HAZELCAST_1.getHost() + ":" + HAZELCAST_1.getMappedPort(5701),
                HAZELCAST_2.getHost() + ":" + HAZELCAST_2.getMappedPort(5701)
        ));
    }

    @Test
    void connectsToHazelcastCluster() {
        assertNotNull(hazelcastInstance);
        assertEquals(2, hazelcastInstance.getCluster().getMembers().size());

        var map = hazelcastInstance.getMap("boot3-integration-test");
        map.put("status", "ok");

        assertEquals("ok", map.get("status"));
    }

    @Test
    void connectsToPostgres() throws Exception {
        assertNotNull(dataSource);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select 1")) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            resultSet.next();
            assertEquals(1, resultSet.getInt(1));
        }
    }
}
