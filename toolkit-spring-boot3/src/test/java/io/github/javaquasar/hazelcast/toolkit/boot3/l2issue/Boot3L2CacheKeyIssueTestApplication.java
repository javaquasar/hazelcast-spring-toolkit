package io.github.javaquasar.hazelcast.toolkit.boot3.l2issue;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackageClasses = {
        IssueUser.class,
        IssueUserGroupWithConverter.class,
        IssueUserGroupScalarNoConverter.class,
        IssueUserGroupManyToOneNoConverter.class,
        IssueSimpleConvertedEntity.class
})
public class Boot3L2CacheKeyIssueTestApplication {
}


