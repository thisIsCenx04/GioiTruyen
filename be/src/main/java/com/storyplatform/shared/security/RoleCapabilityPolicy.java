package com.storyplatform.shared.security;

import java.util.Objects;
import java.util.Set;

public final class RoleCapabilityPolicy
        implements PrivilegedAuthorizationPolicy {

    private static final String ADMIN = "ADMIN";
    private static final String TEAM = "TEAM";

    @Override
    public boolean allows(
            Set<String> roles,
            PrivilegedCapability capability
    ) {
        Objects.requireNonNull(capability, "capability");
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        Set<String> trustedRoles = Set.copyOf(roles);
        if (trustedRoles.contains(ADMIN)) {
            return true;
        }
        return trustedRoles.contains(TEAM);
    }
}
