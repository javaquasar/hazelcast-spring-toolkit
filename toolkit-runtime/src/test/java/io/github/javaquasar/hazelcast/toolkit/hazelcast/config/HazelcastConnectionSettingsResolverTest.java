package io.github.javaquasar.hazelcast.toolkit.hazelcast.config;

import io.github.javaquasar.hazelcast.toolkit.hazelcast.config.HzToolkitProperties.Instance.Mode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HazelcastConnectionSettingsResolverTest {

    @Test
    void sharedSettingsApplyToClientAndMemberModes() {
        HzToolkitProperties toolkit = new HzToolkitProperties();
        toolkit.setClusterName("shared-cluster");
        toolkit.setEnterpriseLicenseKey("shared-license");
        toolkit.getNetwork().setSeedMembers(List.of("10.0.0.1:5701", "10.0.0.2:5701"));

        HazelcastClientProperties client = legacyProperties();

        assertEquals("shared-cluster",
                HazelcastConnectionSettingsResolver.clusterName(toolkit, client, Mode.CLIENT));
        assertEquals("shared-cluster",
                HazelcastConnectionSettingsResolver.clusterName(toolkit, client, Mode.MEMBER));
        assertEquals(List.of("10.0.0.1:5701", "10.0.0.2:5701"),
                HazelcastConnectionSettingsResolver.seedMembers(toolkit, client, Mode.CLIENT));
        assertEquals(List.of("10.0.0.1:5701", "10.0.0.2:5701"),
                HazelcastConnectionSettingsResolver.seedMembers(toolkit, client, Mode.MEMBER));
        assertEquals("shared-license",
                HazelcastConnectionSettingsResolver.enterpriseLicenseKey(toolkit, client));
    }

    @Test
    void modeSpecificSettingsRemainBackwardCompatibleFallbacks() {
        HzToolkitProperties toolkit = new HzToolkitProperties();
        toolkit.getMember().setClusterName("legacy-member-cluster");
        toolkit.getMember().getNetwork().getJoin().setTcpIpMembers(List.of("10.0.1.1:5701"));

        HazelcastClientProperties client = legacyProperties();

        assertEquals("legacy-client-cluster",
                HazelcastConnectionSettingsResolver.clusterName(toolkit, client, Mode.CLIENT));
        assertEquals("legacy-member-cluster",
                HazelcastConnectionSettingsResolver.clusterName(toolkit, client, Mode.MEMBER));
        assertEquals(List.of("10.0.2.1:5701"),
                HazelcastConnectionSettingsResolver.seedMembers(toolkit, client, Mode.CLIENT));
        assertEquals(List.of("10.0.1.1:5701"),
                HazelcastConnectionSettingsResolver.seedMembers(toolkit, client, Mode.MEMBER));
        assertEquals("legacy-license",
                HazelcastConnectionSettingsResolver.enterpriseLicenseKey(toolkit, client));
    }

    private static HazelcastClientProperties legacyProperties() {
        HazelcastClientProperties client = new HazelcastClientProperties();
        client.setClusterName("legacy-client-cluster");
        client.setEnterpriseLicenseKey("legacy-license");
        client.getNetwork().setClusterMembers(List.of("10.0.2.1:5701"));
        return client;
    }
}
