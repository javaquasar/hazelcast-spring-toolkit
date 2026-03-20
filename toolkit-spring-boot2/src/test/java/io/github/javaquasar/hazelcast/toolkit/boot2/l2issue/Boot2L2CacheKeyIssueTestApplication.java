package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackageClasses = {
        LegacyIssueUser.class,
        LegacyIssueUserGroupWithConverter.class,
        LegacyIssueUserGroupManyToOneNoConverter.class
})
public class Boot2L2CacheKeyIssueTestApplication {
}


