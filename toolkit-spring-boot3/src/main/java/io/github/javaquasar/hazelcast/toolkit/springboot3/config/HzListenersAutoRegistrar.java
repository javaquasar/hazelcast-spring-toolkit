package io.github.javaquasar.hazelcast.toolkit.springboot3.config;

import com.hazelcast.core.EntryListener;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.annotation.HzIMapListener;
import io.github.javaquasar.hazelcast.toolkit.scan.reflections.compat.IMapListenerClassesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Scans for @HzIMapListener annotated beans and registers them into target IMaps.
 */
public class HzListenersAutoRegistrar implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(HzListenersAutoRegistrar.class);

    private final HazelcastInstance hazelcastInstance;
    private final IMapListenerClassesScanner listenerScanner;
    private final String basePackage;

    public HzListenersAutoRegistrar(HazelcastInstance hazelcastInstance,
                                    IMapListenerClassesScanner listenerScanner,
                                    String basePackage) {
        this.hazelcastInstance = hazelcastInstance;
        this.listenerScanner = listenerScanner;
        this.basePackage = basePackage;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (basePackage == null || basePackage.isBlank()) {
            log.info("HzListenersAutoRegistrar disabled: no basePackage configured");
            return;
        }

        for (Class<?> listenerClass : listenerScanner.scanIMapListeners(basePackage)) {
            HzIMapListener meta = listenerClass.getAnnotation(HzIMapListener.class);
            Object bean = applicationContext.getBean(listenerClass);

            if (!(bean instanceof EntryListener<?, ?> entryListener)) {
                log.warn("@HzIMapListener class {} is not an EntryListener, skipping", listenerClass.getName());
                continue;
            }

            String mapName = meta.map();
            boolean includeValue = meta.includeValue();
            boolean localOnly = meta.localOnly();

            hazelcastInstance.getMap(mapName).addEntryListener(entryListener, includeValue);
            log.info("Registered Hazelcast EntryListener: listenerClass={}, map={}, includeValue={}, localOnly={} (note: localOnly requires key-based overload)",
                    listenerClass.getName(), mapName, includeValue, localOnly);
        }
    }
}
