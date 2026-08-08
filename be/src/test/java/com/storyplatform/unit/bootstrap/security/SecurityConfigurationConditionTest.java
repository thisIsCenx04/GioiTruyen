package com.storyplatform.unit.bootstrap.security;

import com.storyplatform.bootstrap.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationConditionTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            SecurityConfiguration.class
                    ));

    @Test
    void nonWebMigrationContextDoesNotCreateServletSecurityConfiguration() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(SecurityConfiguration.class));
    }
}
