package com.storyplatform.bootstrap.security;

import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedAuthorizationPolicy;
import com.storyplatform.shared.security.RoleCapabilityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PrivilegedAuthorizationConfiguration {

    @Bean
    PrivilegedAuthorizationPolicy privilegedAuthorizationPolicy() {
        return new RoleCapabilityPolicy();
    }

    @Bean
    JwtPrivilegeEvaluator jwtPrivilegeEvaluator(
            PrivilegedAuthorizationPolicy policy
    ) {
        return new JwtPrivilegeEvaluator(policy);
    }
}
