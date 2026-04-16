package io.github.javaquasar.hazelcast.toolkit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean as a Hazelcast {@link com.hazelcast.map.IMap} listener and
 * registers it automatically at application startup.
 *
 * <p>The annotated bean must implement either
 * {@link com.hazelcast.map.listener.MapListener} or
 * {@link com.hazelcast.core.EntryListener}.  Any other type causes an
 * {@link IllegalStateException} during context refresh.
 *
 * <p><b>Basic usage</b> — distribute all entry events to every cluster member:
 * <pre>{@code
 * @Component
 * @HzIMapListener(map = "orders")
 * public class OrderEventListener implements EntryAddedListener<String, Order> {
 *
 *     @Override
 *     public void entryAdded(EntryEvent<String, Order> event) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p><b>Local-only listener</b> — receive events only for entries whose primary
 * partition is owned by the local member.  Useful for co-located processing and
 * reduces network traffic.  When {@link #localOnly()} is {@code true}, the
 * {@link #includeValue()} flag is ignored because Hazelcast always includes the
 * value for local listeners:
 * <pre>{@code
 * @Component
 * @HzIMapListener(map = "sessions", localOnly = true)
 * public class LocalSessionListener implements MapListener, EntryRemovedListener<String, Session> {
 *     // ...
 * }
 * }</pre>
 *
 * <p>Registration and deregistration are performed by
 * {@code HzListenersAutoRegistrar}, which runs after all Spring singletons are
 * fully initialized (implements {@link org.springframework.beans.factory.SmartInitializingSingleton})
 * and cleans up on context shutdown (implements {@link org.springframework.beans.factory.DisposableBean}).
 * AOP-proxied beans are handled correctly via
 * {@link org.springframework.aop.support.AopUtils#getTargetClass}.
 *
 * @see com.hazelcast.map.listener.MapListener
 * @see com.hazelcast.core.EntryListener
 * @since 0.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HzIMapListener {

    /**
     * Name of the {@link com.hazelcast.map.IMap} to listen on.
     *
     * <p>Must match an existing map name in the connected Hazelcast cluster.
     * The map is retrieved via {@code HazelcastInstance.getMap(name)} — if the
     * named map does not exist it will be created with the default map configuration.
     */
    String map();

    /**
     * Whether entry events should carry the full entry value.
     *
     * <p>When {@code true} (the default), event objects such as
     * {@link com.hazelcast.core.EntryEvent} include the new and old entry values.
     * Set to {@code false} to receive key-only notifications and reduce
     * serialization/network overhead.
     *
     * <p>This flag has no effect when {@link #localOnly()} is {@code true}.
     */
    boolean includeValue() default true;

    /**
     * Whether to register as a local-only listener.
     *
     * <p>A local listener is notified only for entries whose primary partition is
     * owned by the local Hazelcast member, which avoids cross-member event
     * distribution and reduces network overhead in partition-aware processing
     * scenarios.
     *
     * <p>When {@code true}, the listener is registered via
     * {@code IMap.addLocalEntryListener()} and the {@link #includeValue()} flag
     * is ignored (Hazelcast always delivers values to local listeners).
     */
    boolean localOnly() default false;
}
