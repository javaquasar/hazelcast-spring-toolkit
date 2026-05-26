package io.github.javaquasar.hazelcast.toolkit.example.boot3;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.github.javaquasar.hazelcast.toolkit.springboot3.actuator.HazelcastToolkitHealthIndicator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ExampleSpringBoot3Application.class,
        properties = {
                "hazelcast.toolkit.member.instance-name=hz.example.member.smoke",
                "hazelcast.toolkit.member.network.port=0",
                "hazelcast.toolkit.member.network.port-auto-increment=false"
        }
)
@ActiveProfiles("member")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExampleSpringBoot3MemberSmokeTest {

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @AfterAll
    static void shutdownHazelcast() {
        HazelcastClient.shutdownAll();
        Hazelcast.shutdownAll();
    }

    @Test
    void startsExampleApplicationAsHazelcastMember() {
        hazelcastInstance.getMap("example-member-smoke").put("mode", "member");

        Health health = new HazelcastToolkitHealthIndicator(hazelcastInstance).health();

        assertThat(hazelcastInstance.getLocalEndpoint()).isInstanceOf(Member.class);
        assertThat(hazelcastInstance.getCluster().getMembers()).hasSize(1);
        assertThat(hazelcastInstance.getMap("example-member-smoke").get("mode")).isEqualTo("member");
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("mode", "member")
                .containsEntry("clusterName", "dev")
                .containsEntry("memberCount", 1);
    }
}
