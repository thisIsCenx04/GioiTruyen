package com.storyplatform.unit.shared.security;

import com.storyplatform.shared.security.PrivilegedCapability;
import com.storyplatform.shared.security.RoleCapabilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleCapabilityPolicyTest {

    private final RoleCapabilityPolicy policy = new RoleCapabilityPolicy();

    @Test
    void adminInheritsEveryModeratorFinanceSupportAndConfigCapability() {
        for (PrivilegedCapability capability
                : PrivilegedCapability.values()) {
            assertThat(policy.allows(Set.of("ADMIN"), capability))
                    .as("ADMIN -> %s", capability)
                    .isTrue();
        }
    }

    @Test
    void teamCanAccessTeamCapabilities() {
        assertThat(policy.allows(
                Set.of("TEAM"),
                PrivilegedCapability.MODERATION_QUEUE_READ
        )).isTrue();
    }

    @Test
    void userUnknownAndMissingRolesFailClosed() {
        for (PrivilegedCapability capability
                : PrivilegedCapability.values()) {
            assertThat(policy.allows(Set.of("USER"), capability))
                    .isFalse();
            assertThat(policy.allows(Set.of("FINANCE_OPERATOR"), capability))
                    .isFalse();
            assertThat(policy.allows(Set.of("admin"), capability))
                    .isFalse();
            assertThat(policy.allows(Set.of(), capability)).isFalse();
            assertThat(policy.allows(null, capability)).isFalse();
        }
    }
}
