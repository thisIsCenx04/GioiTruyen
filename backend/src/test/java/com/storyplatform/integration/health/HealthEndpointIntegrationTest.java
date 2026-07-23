package com.storyplatform.integration.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.health.mongo.enabled=false",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
@Import(HealthEndpointIntegrationTest.FailingDependencies.class)
class HealthEndpointIntegrationTest {

    private static final String SENSITIVE_TOPOLOGY =
            "private-mongo.internal:27017";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void livenessStaysUpWhenExternalDependenciesFail() throws Exception {
        assertSafeUp("/actuator/health/liveness");
        assertSafeUp("/livez");
    }

    @Test
    void readinessFailsClosedWithoutExposingDependencyState()
            throws Exception {
        assertSafeDown("/actuator/health");
        assertSafeDown("/actuator/health/readiness");
        assertSafeDown("/readyz");
    }

    private void assertSafeUp(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(not(
                        containsString(SENSITIVE_TOPOLOGY)
                )));
    }

    private void assertSafeDown(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(not(
                        containsString(SENSITIVE_TOPOLOGY)
                )))
                .andExpect(content().string(not(
                        containsString("IllegalStateException")
                )));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingDependencies {

        @Bean
        HealthIndicator mongoHealthIndicator() {
            return () -> Health.down()
                    .withDetail("server", SENSITIVE_TOPOLOGY)
                    .withException(new IllegalStateException(
                            "connection credential must stay private"
                    ))
                    .build();
        }

        @Bean
        HealthIndicator redisHealthIndicator() {
            return () -> Health.down()
                    .withDetail("server", "private-redis.internal:6379")
                    .build();
        }
    }
}
