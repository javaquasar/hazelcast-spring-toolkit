package io.github.javaquasar.hazelcast.toolkit.spring.actuator;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearCacheProbeTest {

    @Test
    void resolveIdUsesExplicitSimpleTypes() {
        assertEquals(1, NearCacheProbe.resolveId("1", Integer.class));
        assertEquals(1, NearCacheProbe.resolveId("1", int.class));
        assertEquals(1L, NearCacheProbe.resolveId("1", Long.class));
        assertEquals(1L, NearCacheProbe.resolveId("1", long.class));
        assertEquals((short) 1, NearCacheProbe.resolveId("1", Short.class));
        assertEquals((short) 1, NearCacheProbe.resolveId("1", short.class));
        assertEquals((byte) 1, NearCacheProbe.resolveId("1", Byte.class));
        assertEquals((byte) 1, NearCacheProbe.resolveId("1", byte.class));
        assertEquals("1", NearCacheProbe.resolveId("1", String.class));
    }

    @Test
    void resolveIdUsesStaticValueOfForCustomIdTypes() {
        assertEquals(new CustomId("abc"), NearCacheProbe.resolveId("abc", CustomId.class));
    }

    @Test
    void resolveIdPreservesLegacyFallbackWhenTypeIsUnknown() {
        assertEquals(1L, NearCacheProbe.resolveId("1"));
        assertEquals("abc", NearCacheProbe.resolveId("abc"));
    }

    @Test
    void resolveIdReportsInvalidNumericValuesForKnownTypes() {
        assertThrows(NumberFormatException.class, () -> NearCacheProbe.resolveId("abc", Integer.class));
    }

    @Test
    void checkReturnsClearErrorWhenIdTypeCannotBeResolved() {
        NearCacheProbe probe = new NearCacheProbe(new HzToolkitProperties());

        Map<String, Object> result = probe.check(
                NearCacheProbeTest.class.getName(),
                "1",
                () -> {
                    throw new AssertionError("statistics should not be requested");
                },
                (entityClass, entityId) -> {
                    throw new AssertionError("loader should not be called");
                },
                (entityClass, entityId) -> {
                    throw new AssertionError("evictor should not be called");
                },
                entityClass -> {
                    throw new IllegalArgumentException("Composite ids are not supported");
                }
        );

        assertEquals("ERROR", result.get("status"));
        assertTrue(result.get("error").toString().contains("Failed to resolve entity id type"));
        assertTrue(result.get("error").toString().contains("Composite ids are not supported"));
    }

    private record CustomId(String value) {
        public static CustomId valueOf(String value) {
            return new CustomId(value);
        }
    }
}
