package com.storyplatform.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class JwtPrivilegeEvaluator {

    private static final int MAX_ROLE_COUNT = 8;
    private final PrivilegedAuthorizationPolicy policy;

    public JwtPrivilegeEvaluator(PrivilegedAuthorizationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public boolean allows(Jwt jwt, PrivilegedCapability capability) {
        Objects.requireNonNull(jwt, "jwt");
        try {
            Object claim = jwt.getClaim("roles");
            if (!(claim instanceof java.util.List<?> values)
                    || values.size() > MAX_ROLE_COUNT) {
                return false;
            }
            Set<String> roles = new HashSet<>();
            for (Object value : values) {
                if (!(value instanceof String role)) {
                    return false;
                }
                roles.add(role);
            }
            return policy.allows(Set.copyOf(roles), capability);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
