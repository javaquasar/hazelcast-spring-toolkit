package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.Config;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactEntity;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest.TestHzCompactExplicitEntity;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastMemberFactoryTest {

    @Test
    void createMemberConfigAppliesPropertiesCompactTypesAndCustomizers() throws Exception {
        HazelcastMemberFactory factory = new HazelcastMemberFactory(
                new ReflectionsClassScanner(),
                List.of(config -> config.setProperty("test.customizer.applied", "true"))
        );

        Config config = factory.createMemberConfig(
                "test-member",
                "test-cluster",
                0,
                false,
                "127.0.0.1",
                false,
                false,
                List.of("127.0.0.1:5701"),
                "io.github.javaquasar.hazelcast.toolkit.hazelcast.compacttest"
        );

        assertEquals("test-member", config.getInstanceName());
        assertEquals("test-cluster", config.getClusterName());
        assertFalse(config.isLiteMember());
        assertEquals(0, config.getNetworkConfig().getPort());
        assertFalse(config.getNetworkConfig().isPortAutoIncrement());
        assertEquals("127.0.0.1", config.getNetworkConfig().getPublicAddress());
        assertFalse(config.getNetworkConfig().getJoin().getAutoDetectionConfig().isEnabled());
        assertFalse(config.getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
        assertTrue(config.getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
        assertEquals(List.of("127.0.0.1:5701"), config.getNetworkConfig().getJoin().getTcpIpConfig().getMembers());
        assertEquals("true", config.getProperty("test.customizer.applied"));

        CompactSerializationConfig compactConfig = config.getSerializationConfig().getCompactSerializationConfig();
        assertTrue(registeredClasses(compactConfig).containsKey(TestHzCompactEntity.class));
        assertTrue(registeredClasses(compactConfig).containsKey(TestHzCompactExplicitEntity.class));
    }

    @Test
    void createMemberConfigDisablesTcpIpJoinWhenNoMembersAreConfigured() {
        HazelcastMemberFactory factory = new HazelcastMemberFactory(new ReflectionsClassScanner());

        Config config = factory.createMemberConfig(
                "",
                "test-cluster",
                5701,
                true,
                "",
                false,
                false,
                List.of(),
                null
        );

        assertFalse(config.getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
    }

    @Test
    void createMemberConfigCreatesLiteMemberWhenEnabled() {
        HazelcastMemberFactory factory = new HazelcastMemberFactory(new ReflectionsClassScanner());

        Config config = factory.createMemberConfig(
                "",
                "test-cluster",
                true,
                5701,
                true,
                "",
                false,
                false,
                List.of(),
                null
        );

        assertTrue(config.isLiteMember());
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, ?> registeredClasses(CompactSerializationConfig compactConfig) throws Exception {
        Field field = CompactSerializationConfig.class.getDeclaredField("classToRegistration");
        field.setAccessible(true);
        return (Map<Class<?>, ?>) field.get(compactConfig);
    }
}
