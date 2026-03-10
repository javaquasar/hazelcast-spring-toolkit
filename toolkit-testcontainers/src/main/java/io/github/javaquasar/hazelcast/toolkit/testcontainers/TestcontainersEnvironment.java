package io.github.javaquasar.hazelcast.toolkit.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public abstract class TestcontainersEnvironment {

    private static final String HAZELCAST_CLUSTER_NAME = "core";
    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";

    private static final Network NETWORK = Network.newNetwork();

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withNetwork(NETWORK)
            .withDatabaseName("demo")
            .withUsername("demo")
            .withPassword("demo");

    @Container
    protected static final GenericContainer<?> HAZELCAST_1 = new GenericContainer<>(DockerImageName.parse("hazelcast/hazelcast:5.5.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("hz1")
            .withEnv("HZ_CLUSTERNAME", HAZELCAST_CLUSTER_NAME)
            .withExposedPorts(5701);

    @Container
    protected static final GenericContainer<?> HAZELCAST_2 = new GenericContainer<>(DockerImageName.parse("hazelcast/hazelcast:5.5.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("hz2")
            .withEnv("HZ_CLUSTERNAME", HAZELCAST_CLUSTER_NAME)
            .withExposedPorts(5701);

    protected TestcontainersEnvironment() {
    }

    public static void registerSpringProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> POSTGRES_DRIVER);

        registry.add("hazelcast.client.cluster-name", TestcontainersEnvironment::hazelcastClusterName);
        registry.add("hazelcast.client.network.cluster-members", TestcontainersEnvironment::hazelcastMembers);
        registry.add("hazelcast.client.network.smart-routing", () -> false);
    }

    public static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    public static String dbUsername() {
        return POSTGRES.getUsername();
    }

    public static String dbPassword() {
        return POSTGRES.getPassword();
    }

    public static List<String> hazelcastMembers() {
        return List.of(
                HAZELCAST_1.getHost() + ":" + HAZELCAST_1.getMappedPort(5701),
                HAZELCAST_2.getHost() + ":" + HAZELCAST_2.getMappedPort(5701)
        );
    }

    public static String hazelcastClusterName() {
        return HAZELCAST_CLUSTER_NAME;
    }
}
