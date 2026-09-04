package io.github.javaquasar.hazelcast.toolkit.hazelcast;

import com.hazelcast.client.Client;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;

/**
 * Resolves whether a live Hazelcast instance is a client or a member.
 *
 * <p>The live endpoint takes precedence over configured mode so externally
 * supplied instances used with {@code mode=NONE} are handled correctly.
 *
 * @since 0.12.0
 */
public final class HazelcastInstanceModeResolver {

    private HazelcastInstanceModeResolver() {
    }

    public static Mode resolve(HazelcastInstance hazelcastInstance, Mode configuredMode) {
        Object localEndpoint = hazelcastInstance.getLocalEndpoint();
        if (localEndpoint instanceof Client) {
            return Mode.CLIENT;
        }
        if (localEndpoint instanceof Member) {
            return Mode.MEMBER;
        }
        return configuredMode;
    }
}
