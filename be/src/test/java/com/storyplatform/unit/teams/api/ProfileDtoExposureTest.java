package com.storyplatform.unit.teams.api;

import com.storyplatform.teams.application.ProfileService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileDtoExposureTest {

    private final JsonMapper json = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void publicDtoCannotSerializePrivateFields() throws Exception {
        ProfileService.PublicProfile profile =
                new ProfileService.PublicProfile(
                        "user-id",
                        "Lam Dạ",
                        "Tác giả",
                        null,
                        3
                );

        JsonNode body = json.valueToTree(profile);

        assertThat(body.has("email")).isFalse();
        assertThat(body.has("roles")).isFalse();
        assertThat(body.has("state")).isFalse();
        assertThat(body.path("displayName").asText()).isEqualTo("Lam Dạ");
    }

    @Test
    void privateDtoIncludesOwnerOnlyIdentityFields() {
        ProfileService.PrivateProfile profile =
                new ProfileService.PrivateProfile(
                        "user-id",
                        "reader@example.test",
                        Set.of("USER"),
                        "ACTIVE",
                        "Lam Dạ",
                        "",
                        null,
                        1,
                        Instant.parse("2026-07-24T00:00:00Z")
                );

        JsonNode body = json.valueToTree(profile);

        assertThat(body.path("email").asText())
                .isEqualTo("reader@example.test");
        assertThat(body.path("roles").isArray()).isTrue();
        assertThat(body.path("state").asText()).isEqualTo("ACTIVE");
    }
}
