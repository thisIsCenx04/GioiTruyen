package com.storyplatform.bootstrap.security;

import com.storyplatform.shared.api.SecurityProblemHandler;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String jwtSecret) {
        SecretKey secretKey = new SecretKeySpec(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.security.jwt-secret}") String jwtSecret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
                                "/tags/*/stories",
                                "/home",
                                "/promotions/home",
                                "/search",
                                "/search/suggestions",
                                "/zhihu/sections",
                                "/zhihu/rankings",
                                // The price list and payment options are worth
                                // seeing before creating an account.
                                "/deposit-packages",
                                "/payment-methods",
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
                                HttpMethod.GET,
                                "/public/advertisements/active"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/uploads/**"
                        ).permitAll()
                        // The price table renders for signed-out visitors; buying a
                        // slot and listing your own bookings still require a login.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/promotions/packages"
                        ).permitAll()
                        .requestMatchers("/promotions/**").authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/public/advertisements/*/events"
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
                        // A guest may preview what quests exist; their own board
                        // and any reward claim still require an account.
                        .requestMatchers(HttpMethod.GET, "/quests/preview").permitAll()
                        .requestMatchers("/quests/**").authenticated()
                        .requestMatchers("/rankings/**").permitAll()
                        .requestMatchers("/wallets/**").authenticated()
                        // Opening and reading a top-up belongs to one account.
                        .requestMatchers("/topups", "/topups/**").authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/chapters/*/unlock"
                        ).authenticated()
                        .requestMatchers("/donations").authenticated()
                        .requestMatchers("/teams/*/donations").authenticated()
                        .requestMatchers("/teams/*/rewards").authenticated()
                        .requestMatchers("/referrals/**").authenticated()
                        .requestMatchers(
                                "/teams/applications",
                                "/teams/applications/me",
                                "/teams/*/dashboard"
                        ).authenticated()
                        // Every admin surface exposes user emails, wallet balances
                        // and cash-flow history, so all of them - reads included -
                        // require an authenticated account holding the ADMIN scope.
                        .requestMatchers("/admin/**").hasAuthority("SCOPE_ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/notification-unsubscribe"
                        ).permitAll()
                        .requestMatchers("/notification-preferences")
                        .authenticated()
                        .requestMatchers("/notification-push-subscriptions/**")
                        .authenticated()
                        .requestMatchers("/me", "/me/**").authenticated()
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
