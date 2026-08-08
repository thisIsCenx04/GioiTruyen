package com.storyplatform.bootstrap.security;

import com.storyplatform.shared.api.SecurityProblemHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain applicationSecurity(
            HttpSecurity http,
            SecurityProblemHandler problemHandler
    ) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .csrf(csrf -> csrf.disable())
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
                                "/categories/*/stories",
                                "/home",
                                "/promotions/home",
                                "/search",
                                "/search/suggestions",
                                "/stories",
                                "/stories/*",
                                "/stories/*/chapters",
                                "/chapters/*",
                                "/chapters/*/access",
                                "/users/*",
                                "/teams",
                                "/teams/*",
                                "/comments"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/register",
                                "/auth/email/verify",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/password/forgot",
                                "/auth/password/reset",
                                "/reading-sessions",
                                "/reading-sessions/*/heartbeats",
                                "/reading-sessions/*/complete",
                                "/webhooks/cloudinary",
                                "/webhooks/payments",
                                "/webhooks/withdrawal-payouts"
                        ).permitAll()
                        .requestMatchers(
                                "/auth/oauth2/**"
                        ).permitAll()
                        .requestMatchers(
                                "/auth/logout",
                                "/auth/sessions",
                                "/auth/sessions/*",
                                "/auth/mfa/challenge",
                                "/auth/mfa/verify",
                                "/auth/reauth/grants"
                        ).authenticated()
                        .requestMatchers("/notifications/**")
                        .authenticated()
                        .requestMatchers("/rankings/**").permitAll()
                        .requestMatchers("/wallets/**").authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/chapters/*/unlock"
                        ).authenticated()
                        .requestMatchers("/donations").authenticated()
                        .requestMatchers("/teams/*/rewards").authenticated()
                        .requestMatchers("/referrals/**").authenticated()
                        .requestMatchers(
                                "/teams/applications",
                                "/teams/applications/me",
                                "/teams/*/dashboard"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/admin/dashboard",
                                "/admin/content/**",
                                "/admin/finance/**"
                        ).permitAll()
                        .requestMatchers(
                                "/admin/content/**",
                                "/admin/finance/**"
                        ).authenticated()
                        .requestMatchers("/admin/topups/**").authenticated()
                        .requestMatchers("/admin/withdrawals/**")
                        .authenticated()
                        .requestMatchers(
                                "/admin/configuration/topup-discount"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/notification-unsubscribe"
                        ).permitAll()
                        .requestMatchers("/notification-preferences")
                        .authenticated()
                        .requestMatchers("/notification-push-subscriptions/**")
                        .authenticated()
                        .requestMatchers("/me").authenticated()
                        .requestMatchers(
                                "/me/reading-history",
                                "/me/reading-history/*",
                                "/me/reading-progress/*"
                        ).authenticated()
                        .requestMatchers("/me/library").authenticated()
                        .requestMatchers(
                                "/stories/*/favorite",
                                "/stories/*/follow"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/comments"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/comments/*"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/comments/*"
                        ).authenticated()
                        .requestMatchers(
                                "/reactions/*/*"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/reports"
                        ).authenticated()
                        .requestMatchers("/moderation/**")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/moderation/cases/*/appeals",
                                "/moderation/cases/*/appeals/*/decisions"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/copyright/cases",
                                "/copyright/cases/*/appeals",
                                "/copyright/cases/*/decisions"
                        ).authenticated()
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
                                "/teams/*/withdrawals",
                                "/teams/*/follow",
                                "/team-invitations/*/accept"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/media/upload-signatures"
                        ).authenticated()
                        .anyRequest().denyAll())
                .build();
    }
}
