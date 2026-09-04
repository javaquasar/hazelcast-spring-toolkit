package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

import com.hazelcast.client.Client;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastHibernateInstanceConfigurer.COMMON_INSTANCE_NAME;
import static io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastHibernateInstanceConfigurer.INSTANCE_NAME;
import static io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastHibernateInstanceConfigurer.NATIVE_CLIENT_INSTANCE_NAME;
import static io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HazelcastHibernateInstanceConfigurer.USE_NATIVE_CLIENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastHibernateInstanceConfigurerTest {

    @Test
    void clientUsesNativeClientAndItsActualInstanceName() {
        Map<String, Object> properties = new LinkedHashMap<>();

        HazelcastHibernateInstanceConfigurer.apply(
                properties,
                hazelcastInstance("orders-client", endpoint(Client.class)),
                Mode.MEMBER
        );

        assertEquals(true, properties.get(USE_NATIVE_CLIENT));
        assertEquals("orders-client", properties.get(COMMON_INSTANCE_NAME));
        assertEquals("orders-client", properties.get(NATIVE_CLIENT_INSTANCE_NAME));
        assertFalse(properties.containsKey(INSTANCE_NAME));
    }

    @Test
    void memberUsesServerInstancePropertyAndNeverNativeClient() {
        Map<String, Object> properties = new LinkedHashMap<>();

        HazelcastHibernateInstanceConfigurer.apply(
                properties,
                hazelcastInstance("orders-member", endpoint(Member.class)),
                Mode.CLIENT
        );

        assertEquals(false, properties.get(USE_NATIVE_CLIENT));
        assertEquals("orders-member", properties.get(COMMON_INSTANCE_NAME));
        assertEquals("orders-member", properties.get(INSTANCE_NAME));
        assertFalse(properties.containsKey(NATIVE_CLIENT_INSTANCE_NAME));
    }

    @Test
    void externalInstanceIsDetectedWhenConfiguredModeIsNone() {
        Map<String, Object> properties = new LinkedHashMap<>();

        HazelcastHibernateInstanceConfigurer.apply(
                properties,
                hazelcastInstance("external-member", endpoint(Member.class)),
                Mode.NONE
        );

        assertEquals(false, properties.get(USE_NATIVE_CLIENT));
        assertEquals("external-member", properties.get(INSTANCE_NAME));
    }

    @Test
    void incompleteClientNamePlaceholderFallsBackToActualName() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(USE_NATIVE_CLIENT, "true");
        properties.put(NATIVE_CLIENT_INSTANCE_NAME, "orders-client-");

        HazelcastHibernateInstanceConfigurer.apply(
                properties,
                hazelcastInstance("orders-client-42", endpoint(Client.class)),
                Mode.CLIENT
        );

        assertEquals("orders-client-42", properties.get(NATIVE_CLIENT_INSTANCE_NAME));
    }

    @Test
    void commonInstanceNameIsTranslatedForBothTopologies() {
        Map<String, Object> clientProperties = new LinkedHashMap<>();
        clientProperties.put(COMMON_INSTANCE_NAME, "shared-name");
        Map<String, Object> memberProperties = new LinkedHashMap<>();
        memberProperties.put(COMMON_INSTANCE_NAME, "shared-name");

        HazelcastHibernateInstanceConfigurer.apply(
                clientProperties,
                hazelcastInstance("shared-name", endpoint(Client.class)),
                Mode.CLIENT
        );
        HazelcastHibernateInstanceConfigurer.apply(
                memberProperties,
                hazelcastInstance("shared-name", endpoint(Member.class)),
                Mode.MEMBER
        );

        assertEquals("shared-name", clientProperties.get(NATIVE_CLIENT_INSTANCE_NAME));
        assertEquals("shared-name", memberProperties.get(INSTANCE_NAME));
    }

    @Test
    void contradictoryNativeClientFlagFailsFast() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(USE_NATIVE_CLIENT, "true");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> HazelcastHibernateInstanceConfigurer.apply(
                        properties,
                        hazelcastInstance("orders-member", endpoint(Member.class)),
                        Mode.MEMBER
                )
        );

        assertTrue(failure.getMessage().contains("detected a MEMBER instance"));
        assertTrue(failure.getMessage().contains(USE_NATIVE_CLIENT));
    }

    @Test
    void contradictoryInstanceNameFailsFast() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(INSTANCE_NAME, "another-member");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> HazelcastHibernateInstanceConfigurer.apply(
                        properties,
                        hazelcastInstance("orders-member", endpoint(Member.class)),
                        Mode.MEMBER
                )
        );

        assertTrue(failure.getMessage().contains(INSTANCE_NAME));
    }

    private static HazelcastInstance hazelcastInstance(String name, Object endpoint) {
        return (HazelcastInstance) Proxy.newProxyInstance(
                HazelcastInstance.class.getClassLoader(),
                new Class<?>[]{HazelcastInstance.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getLocalEndpoint" -> endpoint;
                    case "toString" -> name;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T endpoint(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        return null;
    }
}
