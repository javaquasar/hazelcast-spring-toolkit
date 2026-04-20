package io.github.javaquasar.hazelcast.toolkit.spring.test.l2issue;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackageClasses = {
        SharedIssueUser.class,
        SharedIssueUserGroupWithConverter.class,
        SharedIssueUserGroupScalarNoConverter.class,
        SharedIssueUserGroupManyToOneNoConverter.class,
        SharedIssueSimpleConvertedEntity.class
})
public class SharedL2CacheKeyIssueTestApplication {
}
