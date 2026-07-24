package com.storyplatform.integration.media;

import com.storyplatform.media.application.UploadSignatureOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .status;

@SpringBootTest(properties = {
        "app.media.cloudinary.enabled=true",
        "app.media.cloudinary.cloud-name=test-cloud",
        "app.media.cloudinary.api-key=test-key",
        "app.media.cloudinary.upload-preset=restricted",
        "app.media.cloudinary.root-folder=gioitruyen/test",
        "app.media.cloudinary.signature-ttl=10m"
})
@AutoConfigureMockMvc
class UploadSignatureIntegrationTest {

    private static final String USER_ID =
            "00000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadSignatureOperations signatures;

    @Test
    void authenticatedRequestReturnsNoStoreRestrictedGrant()
            throws Exception {
        when(signatures.issue(eq(USER_ID), any())).thenReturn(
                new UploadSignatureOperations.UploadGrant(
                        "00000000-0000-4000-8000-000000000003",
                        "https://api.cloudinary.com/v1_1/test-cloud"
                                + "/image/authenticated",
                        "test-key",
                        "a".repeat(64),
                        Instant.parse("2026-07-24T00:10:00Z"),
                        5242880,
                        Set.of("image/png"),
                        Map.of("timestamp", "1784851200")
                )
        );

        mockMvc.perform(post("/media/upload-signatures")
                        .with(jwt().jwt(token ->
                                token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerType": "USER",
                                  "ownerId": "%s",
                                  "purpose": "AVATAR",
                                  "contentType": "image/png",
                                  "sizeBytes": 1024,
                                  "sha256": "%s"
                                }
                                """.formatted(USER_ID, "a".repeat(64))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        "Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")
                ))
                .andExpect(jsonPath("$.apiKey").value("test-key"))
                .andExpect(jsonPath("$.signature").value("a".repeat(64)))
                .andExpect(jsonPath("$.apiSecret").doesNotExist());
    }

    @Test
    void anonymousRequestCannotReachTheSigningOperation()
            throws Exception {
        mockMvc.perform(post("/media/upload-signatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
