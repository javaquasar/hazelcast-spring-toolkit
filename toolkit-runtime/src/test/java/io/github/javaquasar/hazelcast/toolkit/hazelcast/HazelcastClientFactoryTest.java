package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.RoutingMode;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.ReflectionsClassScanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HazelcastClientFactoryTest {

    private final HazelcastClientFactory factory = new HazelcastClientFactory(new ReflectionsClassScanner());

    @Test
    void smartRoutingUsesDefaultAllMembersRoutingMode() {
        ClientConfig config = createClientConfig(true);

        assertEquals(RoutingMode.ALL_MEMBERS,
                config.getNetworkConfig().getClusterRoutingConfig().getRoutingMode());
    }

    @Test
    void disabledSmartRoutingUsesSingleMemberRoutingMode() {
        ClientConfig config = createClientConfig(false);

        assertEquals(RoutingMode.SINGLE_MEMBER,
                config.getNetworkConfig().getClusterRoutingConfig().getRoutingMode());
    }

    private ClientConfig createClientConfig(boolean smartRouting) {
        return factory.createClientConfig(
                "test-client",
                "test-cluster",
                List.of("127.0.0.1:5701"),
                smartRouting,
                null
        );
    }
}
