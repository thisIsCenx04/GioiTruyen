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
import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    /** HS256 is defined over a 256-bit key; a shorter one is not a valid key. */
    private static final int MINIMUM_JWT_SECRET_BYTES = 32;

    /**
     * The one key both halves of the token flow use.
     *
     * <p>Validated here rather than left to fail deep inside Nimbus: an absent
     * key used to fall back to a value committed in the repository, so a
     * deployment that forgot to set {@code JWT_SIGNING_KEY} signed its tokens
     * with a secret anyone could read - and an ADMIN token is only a signature
     * away from full access to /admin/**.
     */
    @Bean
    SecretKey jwtSigningKey(@Value("${app.security.jwt-secret}") String jwtSecret) {
        byte[] key = jwtSecret == null
                ? new byte[0]
                : jwtSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (key.length == 0) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY is not set. Tokens cannot be signed without it. "
                            + "Set JWT_SIGNING_KEY (at least " + MINIMUM_JWT_SECRET_BYTES
                            + " bytes) in the environment before starting the backend.");
        }
        if (key.length < MINIMUM_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY is only " + key.length + " bytes; HS256 requires at least "
                            + MINIMUM_JWT_SECRET_BYTES + ".");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cross-origin callers, named one by one.
     *
     * <p>The previous {@code "*"} pattern combined with allowed credentials made
     * Spring echo back whichever Origin asked, so any website on the internet
     * could call this API as the visitor and read the reply. Credentials are
     * still allowed - the deployed frontend shares the API's origin and never
     * needs CORS at all, so the cost of naming the rest is small.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.allowed-origins}") String allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS cannot be \"*\" while credentials are allowed. "
                            + "List each origin that may call the API.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
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
            SecurityProblemHandler problemHandler,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Spring Security registers its own /logout handler by default,
                // and it answers a browser-style 302 to /login?logout. That
                // filter runs before the dispatcher, so AuthController's POST
                // /logout - the one that actually revokes the refresh tokens -
                // was never reached and the API returned a redirect to a JSON
                // caller. There is no server session here to end; the tokens are
                // the session.
                .logout(logout -> logout.disable())
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
                                // What a combo costs is part of the story page, so
                                // a visitor sees the real price before signing in.
                                // Only the "already purchased" flag needs an
                                // account, and it reads false without one.
                                "/stories/*/combo-status",
                                // Resolving "chuong-12" to a chapter is as
                                // public as the chapter list it replaced.
                                "/stories/*/chapters/by-number/*",
                                "/chapters/*",
                                "/chapters/*/access",
                                // Bản đọc tự động công khai đúng bằng chương nó
                                // đọc: chương trả phí chưa mở khoá về đây với
                                // phần chữ rỗng, nên không nghe lậu được.
                                "/chapters/*/tts/manifest",
                                "/chapters/*/tts/*.opus",
                                // Reading the discussion is public; posting is not.
                                "/comments",
                                "/users/*",
                                "/teams",
                                "/teams/*",
                                // The team profile's story list. "/teams/*" does
                                // not cover a deeper segment, and an undeclared
                                // path falls through to denyAll and answers 403 -
                                // which reads as a permission problem rather than
                                // the missing declaration it actually is.
                                "/teams/*/published-stories",
                                // The community chat panel: readable by a guest,
                                // writable only with an account (declared below).
                                "/community/messages",
                                // The PR quest board. Someone deciding whether to
                                // sign up should see what is on offer first;
                                // claiming and submitting need an account.
                                "/pr-quests",
                                "/comments"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/public/advertisements/active"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/uploads/**",
                                // Banner images every visitor sees at the top of a page.
                                "/site/banners"
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
                                "/register",
                                "/login",
                                "/refresh",
                                "/email/verify",
                                "/password/forgot",
                                "/password/reset",
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
                                "/logout",
                                "/sessions",
                                "/sessions/*",
                                "/mfa/challenge",
                                "/mfa/verify",
                                "/reauth/grants"
                        ).authenticated()
                        .requestMatchers("/notifications/**")
                        .authenticated()
                        // Asking to become a publisher, and reading your own request.
                        .requestMatchers("/author-applications", "/author-applications/**")
                        .authenticated()
                        // GET /comments is permitted above; writing needs an account.
                        .requestMatchers("/comments", "/comments/**").authenticated()
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
                        // The publisher workspace. Membership is what actually
                        // authorises these, and TeamWorkspaceController checks it
                        // per request - the chain only enforces "signed in",
                        // because team roles are rows, not JWT scopes.
                        .requestMatchers(
                                "/teams/applications",
                                "/teams/applications/me",
                                "/teams/*/access",
                                "/teams/*/dashboard",
                                "/teams/*/analytics",
                                "/teams/*/stories",
                                "/teams/*/stories/**",
                                "/teams/*/earnings",
                                "/teams/*/supporters",
                                // PR quests. The chain only checks "signed in";
                                // TeamPrQuestController then requires OWNER or
                                // MANAGER, because these endpoints spend money.
                                "/teams/*/pr-quests",
                                "/teams/*/pr-quests/**",
                                // Claiming, submitting and disputing a PR quest.
                                // Reading the board is public, declared above.
                                "/pr-quests/mine",
                                "/pr-quests/proof",
                                "/pr-quests/*/claim",
                                "/pr-quests/claims/**",
                                // Team settings. The chain only checks "signed
                                // in"; TeamWorkspaceController then requires the
                                // caller to be the OWNER, since team roles are
                                // rows in team_members rather than JWT scopes.
                                //
                                // Missing entries here do not 404 - they fall
                                // through to anyRequest().denyAll() and answer
                                // 403 "Bạn không có quyền thực hiện thao tác
                                // này", which reads exactly like a failed role
                                // check and sent the owner hunting for a
                                // permission problem that did not exist.
                                "/teams/*/profile",
                                "/teams/*/avatar",
                                "/teams/*/members",
                                "/teams/*/members/*"
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
                        // Thêm thể loại từ luồng upload: người upload thường là
                        // chủ nhóm chứ không phải quản trị viên, nên màn hình
                        // quản trị thể loại nằm ngoài tầm với của họ.
                        .requestMatchers(HttpMethod.POST, "/genres").authenticated()
                        .requestMatchers(
                                "/stories/*/favorite",
                                "/stories/*/follow",
                                // Spending gems on a story, and reading back
                                // what you personally gave it.
                                "/stories/*/recommend",
                                // Buying every chapter at once. This was missing,
                                // and the chain ends in denyAll, so the combo
                                // button answered 403 for everyone - the endpoint
                                // was never reachable at all.
                                "/stories/*/combo-purchase"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/comments",
                                "/community/messages"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/comments/*"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/comments/*"
                        ).authenticated()
                        // Reading the heart count is public - it is part of the
                        // comment a guest can already see. Only pressing it,
                        // which needs an account to attribute the like to,
                        // requires a login.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/reactions/*/*"
                        ).permitAll()
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
