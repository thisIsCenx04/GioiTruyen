package com.storyplatform.unit.media.infrastructure;

import com.storyplatform.media.infrastructure
        .CloudinaryUploadParameterSigner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryUploadParameterSignerTest {

    private final CloudinaryUploadParameterSigner signer =
            new CloudinaryUploadParameterSigner("0123456789abcdef");

    @Test
    void sortsParametersAndUsesSha256() {
        String signature = signer.sign(Map.of(
                "timestamp", "1315060510",
                "public_id", "sample_image",
                "eager", "w_400,h_300,c_pad|w_260,h_200,c_crop"
        ));

        assertThat(signature).isEqualTo(
                "7f9038ed614dd7731cec7f0717822661"
                        + "a0fafb85eafa8c2347ed0e0066ff27fa"
        );
    }

    @Test
    void anySignedParameterTamperChangesTheSignature() {
        String original = signer.sign(Map.of(
                "context", "purpose=avatar|declared_sha256=" + "a".repeat(64),
                "timestamp", "1315060510"
        ));
        String tampered = signer.sign(Map.of(
                "context", "purpose=story_cover|declared_sha256="
                        + "a".repeat(64),
                "timestamp", "1315060510"
        ));

        assertThat(tampered).isNotEqualTo(original);
    }

    @Test
    void rejectsWeakSecretsAndEmptyParameterSets() {
        assertThatThrownBy(() ->
                new CloudinaryUploadParameterSigner(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new CloudinaryUploadParameterSigner("too-short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void omitsNullAndBlankParametersFromTheCanonicalPayload() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("timestamp", "1315060510");
        parameters.put("empty", "");
        parameters.put("missing", null);

        assertThat(signer.sign(parameters)).isEqualTo(
                signer.sign(Map.of("timestamp", "1315060510"))
        );
    }
}
