package io.github.javaquasar.hazelcast.toolkit.boot3;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.testcontainers.TestcontainersEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = TestApplication.class)
class Boot3IntegrationTest extends TestcontainersEnvironment {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestcontainersEnvironment.registerSpringProperties(registry);
    }

    @Test
    void connectsToHazelcastCluster() {
        assertNotNull(hazelcastInstance);
        assertFalse(hazelcastInstance.getCluster().getMembers().isEmpty());

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
