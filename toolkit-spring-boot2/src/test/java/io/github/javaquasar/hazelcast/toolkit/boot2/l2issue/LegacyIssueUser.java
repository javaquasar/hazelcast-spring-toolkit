package io.github.javaquasar.hazelcast.toolkit.boot2.l2issue;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "legacy_l2_issue_user")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "legacy-l2-issue-user")
public class LegacyIssueUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    protected LegacyIssueUser() {
    }

    public LegacyIssueUser(String username) {
        this.username = username;
    }

    public Long getId() {
        return id;
    }
}


