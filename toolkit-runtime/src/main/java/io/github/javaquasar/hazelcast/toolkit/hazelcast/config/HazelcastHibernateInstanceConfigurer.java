package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.HazelcastInstanceModeResolver;
import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;

import java.util.Locale;
import java.util.Map;

/**
 * Binds Hazelcast Hibernate native region factories to the live client or member.
 *
 * @since 0.12.0
 */
public final class HazelcastHibernateInstanceConfigurer {

    /**
     * Toolkit-level topology-neutral alias accepted for both client and member modes.
     */
    public static final String COMMON_INSTANCE_NAME = "hibernate.cache.hazelcast.instance.name";
    public static final String INSTANCE_NAME = "hibernate.cache.hazelcast.instance_name";
    public static final String USE_NATIVE_CLIENT = "hibernate.cache.hazelcast.use_native_client";
    public static final String NATIVE_CLIENT_INSTANCE_NAME =
            "hibernate.cache.hazelcast.native_client_instance_name";

    private HazelcastHibernateInstanceConfigurer() {
    }

    public static void apply(
            Map<String, Object> properties,
            HazelcastInstance hazelcastInstance,
            Mode configuredMode) {
        Mode actualMode = HazelcastInstanceModeResolver.resolve(hazelcastInstance, configuredMode);
        String actualInstanceName = hazelcastInstance.getName();
        if (actualInstanceName == null || actualInstanceName.isBlank()) {
            throw new IllegalStateException(
                    "Hazelcast toolkit Hibernate L2 requires a named HazelcastInstance"
            );
        }
        String instanceName = commonInstanceName(properties, actualInstanceName, actualMode);

        if (actualMode == Mode.CLIENT) {
            applyNativeClient(properties, instanceName);
            return;
        }
        if (actualMode == Mode.MEMBER) {
            applyMember(properties, instanceName);
            return;
        }
        throw new IllegalStateException(
                "Hazelcast toolkit Hibernate L2 could not identify the supplied " +
                "HazelcastInstance as a client or member"
        );
    }

    private static void applyNativeClient(Map<String, Object> properties, String instanceName) {
        validateBooleanProperty(properties, true, Mode.CLIENT);
        properties.putIfAbsent(USE_NATIVE_CLIENT, true);
        applyInstanceName(properties, NATIVE_CLIENT_INSTANCE_NAME, instanceName, Mode.CLIENT);
    }

    private static String commonInstanceName(
            Map<String, Object> properties,
            String actualInstanceName,
            Mode actualMode) {
        Object configured = properties.get(COMMON_INSTANCE_NAME);
        if (isFallbackValue(configured)) {
            properties.put(COMMON_INSTANCE_NAME, actualInstanceName);
            return actualInstanceName;
        }
        String configuredName = String.valueOf(configured).trim();
        if (!actualInstanceName.equals(configuredName)) {
            throw incompatibleProperty(actualMode, COMMON_INSTANCE_NAME, configured);
        }
        return configuredName;
    }

    private static void applyMember(Map<String, Object> properties, String instanceName) {
        validateBooleanProperty(properties, false, Mode.MEMBER);
        properties.putIfAbsent(USE_NATIVE_CLIENT, false);
        applyInstanceName(properties, INSTANCE_NAME, instanceName, Mode.MEMBER);
    }

    private static void validateBooleanProperty(
            Map<String, Object> properties,
            boolean expected,
            Mode actualMode) {
        Object configured = properties.get(USE_NATIVE_CLIENT);
        if (configured == null) {
            return;
        }

        String value = String.valueOf(configured).trim().toLowerCase(Locale.ROOT);
        if (!value.equals("true") && !value.equals("false")) {
            throw incompatibleProperty(actualMode, USE_NATIVE_CLIENT, configured);
        }
        if (Boolean.parseBoolean(value) != expected) {
            throw incompatibleProperty(actualMode, USE_NATIVE_CLIENT, configured);
        }
    }

    private static void applyInstanceName(
            Map<String, Object> properties,
            String propertyName,
            String actualName,
            Mode actualMode) {
        Object configured = properties.get(propertyName);
        if (isFallbackValue(configured)) {
            properties.put(propertyName, actualName);
            return;
        }
        if (!actualName.equals(String.valueOf(configured).trim())) {
            throw incompatibleProperty(actualMode, propertyName, configured);
        }
    }

    private static boolean isFallbackValue(Object configured) {
        if (configured == null) {
            return true;
        }
        String value = String.valueOf(configured).trim();
        return value.isEmpty() || value.endsWith("-") || value.contains("${");
    }

    private static IllegalStateException incompatibleProperty(
            Mode actualMode,
            String propertyName,
            Object configuredValue) {
        return new IllegalStateException(
                "Hazelcast toolkit Hibernate L2 detected a " + actualMode +
                " instance, but " + propertyName + " is set to '" + configuredValue + "'. " +
                "Remove mode-specific Hibernate Hazelcast properties and let the toolkit " +
                "derive them from the live Hazelcast topology."
        );
    }
}
