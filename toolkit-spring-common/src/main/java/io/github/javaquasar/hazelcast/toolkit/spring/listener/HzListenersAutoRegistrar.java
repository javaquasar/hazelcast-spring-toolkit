package io.github.javaquasar.hazelcast.toolkit.spring.listener;

import com.hazelcast.core.EntryListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.MapListener;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzIMapListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Discovers Spring beans annotated with {@link HzIMapListener} and registers them into target IMaps.
 */
public class HzListenersAutoRegistrar implements SmartInitializingSingleton, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HzListenersAutoRegistrar.class);

    private final HazelcastInstance hazelcastInstance;
    private final ListableBeanFactory beanFactory;
    private final Map<RegistrationKey, UUID> registrations = new LinkedHashMap<>();

    public HzListenersAutoRegistrar(HazelcastInstance hazelcastInstance,
                                    ListableBeanFactory beanFactory) {
        this.hazelcastInstance = hazelcastInstance;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> candidates = beanFactory.getBeansWithAnnotation(HzIMapListener.class);

        if (candidates.isEmpty()) {
            log.debug("No @HzIMapListener beans found");
            return;
        }

        for (Map.Entry<String, Object> candidate : candidates.entrySet()) {
            Object bean = candidate.getValue();
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            HzIMapListener meta = AnnotationUtils.findAnnotation(targetClass, HzIMapListener.class);

            if (meta == null) {
                log.warn(
                        "Bean {} was returned as @HzIMapListener candidate but no annotation was found on targetClass={}",
                        candidate.getKey(),
                        targetClass.getName()
                );
                continue;
            }

            if (!(bean instanceof MapListener) && !(bean instanceof EntryListener<?, ?>)) {
                throw new IllegalStateException(
                        "@HzIMapListener bean %s must implement MapListener or EntryListener"
                                .formatted(targetClass.getName())
                );
            }

            IMap<Object, Object> map = hazelcastInstance.getMap(meta.map());
            UUID registrationId = registerListener(map, bean, meta);

            registrations.put(new RegistrationKey(meta.map(), registrationId), registrationId);

            if (meta.localOnly()) {
                log.info(
                        "Registered LOCAL Hazelcast listener: listenerClass={}, map={}",
                        targetClass.getName(),
                        meta.map()
                );
            } else {
                log.info(
                        "Registered Hazelcast listener: listenerClass={}, map={}, includeValue={}",
                        targetClass.getName(),
                        meta.map(),
                        meta.includeValue()
                );
            }
        }
    }

    private UUID registerListener(IMap<Object, Object> map, Object bean, HzIMapListener meta) {
        if (bean instanceof MapListener mapListener) {
            return meta.localOnly()
                    ? map.addLocalEntryListener(mapListener)
                    : map.addEntryListener(mapListener, meta.includeValue());
        }

        EntryListener<?, ?> entryListener = (EntryListener<?, ?>) bean;
        return meta.localOnly()
                ? map.addLocalEntryListener(entryListener)
                : map.addEntryListener(entryListener, meta.includeValue());
    }

    @Override
    public void destroy() {
        registrations.forEach((key, registrationId) -> {
            try {
                hazelcastInstance.getMap(key.mapName()).removeEntryListener(registrationId);
                log.debug("Deregistered Hazelcast listener: map={}, registrationId={}", key.mapName(), registrationId);
            } catch (Exception e) {
                log.warn("Failed to deregister Hazelcast listener from map '{}' (registrationId={}): {}",
                        key.mapName(), registrationId, e.getMessage());
            }
        });
        registrations.clear();
    }

    private record RegistrationKey(String mapName, UUID registrationId) {
    }
}
