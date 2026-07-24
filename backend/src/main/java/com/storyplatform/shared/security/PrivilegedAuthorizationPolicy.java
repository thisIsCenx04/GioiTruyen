package com.storyplatform.shared.security;

import java.util.Set;

public interface PrivilegedAuthorizationPolicy {

    boolean allows(Set<String> roles, PrivilegedCapability capability);
}
