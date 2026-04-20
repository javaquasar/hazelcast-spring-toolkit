package io.github.javaquasar.hazelcast.toolkit.metrics.spring;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Micrometer binder exposing global Hibernate second-level cache statistics.
 *
 * <p>The binder reads Hibernate statistics reflectively so it can be reused across
 * the Boot 2 ({@code javax.persistence} / Hibernate 5) and Boot 3 / 4
 * ({@code jakarta.persistence} / Hibernate 6) stacks without a separate implementation.
 *
 * <p>Exported metrics:
 * <ul>
 *   <li>{@code hazelcast.toolkit.hibernate.l2.hit.count}</li>
 *   <li>{@code hazelcast.toolkit.hibernate.l2.miss.count}</li>
 *   <li>{@code hazelcast.toolkit.hibernate.l2.put.count}</li>
 *   <li>{@code hazelcast.toolkit.hibernate.query.cache.hit.count}</li>
 *   <li>{@code hazelcast.toolkit.hibernate.query.cache.miss.count}</li>
 *   <li>{@code hazelcast.toolkit.hibernate.query.cache.put.count}</li>
 *   <li>{@code hazelcast.toolkit.hibernate.statistics.enabled}</li>
 * </ul>
 */
public class HibernateL2MetricsBinder implements MeterBinder {

    private final Object entityManagerFactory;
    private final HzToolkitProperties properties;
    private final Set<MeterRegistry> boundRegistries = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> statisticsReferences = Collections.newSetFromMap(new IdentityHashMap<>());

    public HibernateL2MetricsBinder(Object entityManagerFactory,
                                    HzToolkitProperties properties) {
        this.entityManagerFactory = entityManagerFactory;
        this.properties = properties;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        synchronized (boundRegistries) {
            if (!boundRegistries.add(registry)) {
                return;
            }
        }
        Object statistics = extractStatistics();
        statisticsReferences.add(statistics);
        Tags tags = Tags.of(
                "regionFactory",
                properties.getHibernate().getL2().getRegionFactory().name()
        );

        FunctionCounter.builder("hazelcast.toolkit.hibernate.l2.hit.count", statistics, stats -> readLong(stats, "getSecondLevelCacheHitCount"))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder("hazelcast.toolkit.hibernate.l2.miss.count", statistics, stats -> readLong(stats, "getSecondLevelCacheMissCount"))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder("hazelcast.toolkit.hibernate.l2.put.count", statistics, stats -> readLong(stats, "getSecondLevelCachePutCount"))
                .tags(tags)
                .register(registry);

        FunctionCounter.builder("hazelcast.toolkit.hibernate.query.cache.hit.count", statistics, stats -> readLong(stats, "getQueryCacheHitCount"))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder("hazelcast.toolkit.hibernate.query.cache.miss.count", statistics, stats -> readLong(stats, "getQueryCacheMissCount"))
                .tags(tags)
                .register(registry);
        FunctionCounter.builder("hazelcast.toolkit.hibernate.query.cache.put.count", statistics, stats -> readLong(stats, "getQueryCachePutCount"))
                .tags(tags)
                .register(registry);

        Gauge.builder("hazelcast.toolkit.hibernate.statistics.enabled", statistics, stats -> readBoolean(stats, "isStatisticsEnabled") ? 1.0d : 0.0d)
                .tags(tags)
                .register(registry);
    }

    private Object extractStatistics() {
        try {
            Method unwrap = entityManagerFactory.getClass().getMethod("unwrap", Class.class);
            Class<?> sessionFactoryClass = Class.forName("org.hibernate.SessionFactory");
            Object sessionFactory = unwrap.invoke(entityManagerFactory, sessionFactoryClass);
            return sessionFactoryClass.getMethod("getStatistics").invoke(sessionFactory);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to unwrap Hibernate SessionFactory statistics", ex);
        }
    }

    private double readLong(Object target, String methodName) {
        try {
            Number value = (Number) target.getClass().getMethod(methodName).invoke(target);
            return value.doubleValue();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to read Hibernate statistics method " + methodName, ex);
        }
    }

    private boolean readBoolean(Object target, String methodName) {
        try {
            return (Boolean) target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to read Hibernate statistics method " + methodName, ex);
        }
    }
}
