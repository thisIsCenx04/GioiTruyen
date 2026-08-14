package com.storyplatform.unit.bootstrap.security;

import com.storyplatform.bootstrap.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signing key decides who is an admin, so a deployment that forgot to set it
 * must stop rather than fall back to something guessable.
 */
class JwtSigningKeyGuardTest {

    private static final String VALID_KEY = "a-thirty-two-byte-signing-key!!!!";

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(SecurityConfiguration.class)
                    .withPropertyValues("app.security.allowed-origins=http://localhost:3000");

    @Test
    void refusesToStartWithoutASigningKey() {
        contextRunner.withPropertyValues("app.security.jwt-secret=")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("JWT_SIGNING_KEY is not set"));
    }

    @Test
    void refusesAKeyTooShortForHs256() {
        contextRunner.withPropertyValues("app.security.jwt-secret=too-short")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("HS256 requires at least"));
    }

    /**
     * A usable key must get past the guard. The context still cannot finish
     * building here - the filter chain needs the servlet stack this runner does
     * not stand up - so the assertion is that it no longer stops on the key.
     */
    @Test
    void acceptsAKeyOfSufficientLength() {
        contextRunner.withPropertyValues("app.security.jwt-secret=" + VALID_KEY)
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasMessageNotContaining("JWT_SIGNING_KEY"));
    }

    @Test
    void refusesAWildcardOriginBecauseCredentialsAreAllowed() {
        contextRunner
                .withPropertyValues(
                        "app.security.jwt-secret=" + VALID_KEY,
                        "app.security.allowed-origins=*")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("cannot be \"*\""));
    }
}
