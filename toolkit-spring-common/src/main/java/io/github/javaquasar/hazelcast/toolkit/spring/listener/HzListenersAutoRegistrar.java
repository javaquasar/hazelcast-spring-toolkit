package io.github.javaquasar.hazelcast.toolkit.spring.listener;

import com.hazelcast.core.EntryListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
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

            if (!(bean instanceof EntryListener<?, ?> entryListener)) {
                throw new IllegalStateException(
                        "@HzIMapListener bean %s must implement EntryListener"
                                .formatted(targetClass.getName())
                );
            }

            IMap<Object, Object> map = hazelcastInstance.getMap(meta.map());
            UUID registrationId = meta.localOnly()
                    ? map.addLocalEntryListener(entryListener)
                    : map.addEntryListener(entryListener, meta.includeValue());

            registrations.put(new RegistrationKey(meta.map(), registrationId), registrationId);

            if (meta.localOnly()) {
                log.info(
                        "Registered LOCAL Hazelcast EntryListener: listenerClass={}, map={}",
                        targetClass.getName(),
                        meta.map()
                );
            } else {
                log.info(
                        "Registered Hazelcast EntryListener: listenerClass={}, map={}, includeValue={}",
                        targetClass.getName(),
                        meta.map(),
                        meta.includeValue()
                );
            }
        }
    }

    @Override
    public void destroy() {
        registrations.forEach((key, registrationId) -> {
            try {
                hazelcastInstance.getMap(key.mapName()).removeEntryListener(registrationId);
            } catch (Exception ex) {
                log.debug(
                        "Failed to remove Hazelcast EntryListener: map={}, registrationId={}",
                        key.mapName(),
                        registrationId,
                        ex
                );
            }
        });
        registrations.clear();
    }

    private record RegistrationKey(String mapName, UUID registrationId) {
    }
}
