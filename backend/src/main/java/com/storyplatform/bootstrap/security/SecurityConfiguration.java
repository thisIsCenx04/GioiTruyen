package com.storyplatform.bootstrap.security;

import com.storyplatform.shared.api.SecurityProblemHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain applicationSecurity(
            HttpSecurity http,
            SecurityProblemHandler problemHandler
    ) throws Exception {
        return http
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/auth/register",
                        "/auth/email/verify",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/password/forgot",
                        "/auth/password/reset",
                        "/auth/logout",
                        "/auth/sessions",
                        "/auth/sessions/*",
                        "/auth/mfa/challenge",
                        "/auth/mfa/verify",
                        "/auth/reauth/grants",
                        "/me",
                        "/teams",
                        "/teams/*"
                ))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/livez",
                                "/readyz",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/categories",
                                "/home",
                                "/search",
                                "/search/suggestions",
                                "/stories",
                                "/stories/*",
                                "/stories/*/chapters",
                                "/users/*",
                                "/teams",
                                "/teams/*"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/register",
                                "/auth/email/verify",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/password/forgot",
                                "/auth/password/reset"
                        ).permitAll()
                        .requestMatchers(
                                "/auth/logout",
                                "/auth/sessions",
                                "/auth/sessions/*",
                                "/auth/mfa/challenge",
                                "/auth/mfa/verify",
                                "/auth/reauth/grants"
                        ).authenticated()
                        .requestMatchers("/me").authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/teams"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/teams/*"
                        ).authenticated()
                        .requestMatchers(
                                "/teams/*/members",
                                "/teams/*/members/*",
                                "/teams/*/members/*/permissions",
                                "/teams/*/follow",
                                "/team-invitations/*/accept"
                        ).authenticated()
                        .anyRequest().denyAll())
                .build();
    }
}
