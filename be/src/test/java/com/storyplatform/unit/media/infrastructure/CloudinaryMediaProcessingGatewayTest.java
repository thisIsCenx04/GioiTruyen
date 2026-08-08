package com.storyplatform.unit.media.infrastructure;

import com.storyplatform.media.application.MediaGatewayException;
import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import com.storyplatform.media.infrastructure.CloudinaryHttpTransport;
import com.storyplatform.media.infrastructure.CloudinaryMediaProcessingGateway;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryMediaProcessingGatewayTest {

    @Test
    void downloadsAuthenticatedSourceWithSignedDeliveryUrl() {
        byte[] source = jpeg();
        StubTransport http = new StubTransport();
        http.respond(200, source);
        var gateway = gateway(http);

        assertThat(gateway.downloadOriginal(candidate(), 100))
                .containsExactly(source);
        assertThat(http.requests()).singleElement().satisfies(request -> {
            assertThat(request.url())
                    .startsWith("https://res.cloudinary.com/")
                    .contains("/image/authenticated/s--")
                    .endsWith("/v42/restricted/source.jpg");
        });
    }

    @Test
    void publishesNewNormalizedAssetWithSignedBoundedTransformations() {
        StubTransport http = new StubTransport();
        http.respond(
                200,
                ("{\"public_id\":\"published/user/owner/avatar/asset\","
                        + "\"version\":7,\"format\":\"png\",\"bytes\":20,"
                        + "\"eager\":[{},{},{}]}").getBytes(
                        StandardCharsets.UTF_8
                )
        );
        var gateway = gateway(http);

        var published = gateway.publishNormalized(candidate(), jpeg());

        assertThat(published.publicId())
                .isEqualTo("published/user/owner/avatar/asset");
        StubRequest request = http.requests().getFirst();
        assertThat(request.url())
                .isEqualTo("https://api.cloudinary.com/v1_1/cloud/image/upload");
        String body = new String(
                request.body(),
                StandardCharsets.ISO_8859_1
        );
        assertThat(body)
                .contains("name=\"api_key\"\r\n\r\napi-key")
                .contains("name=\"signature\"")
                .contains("fl_force_strip")
                .contains("name=\"type\"\r\n\r\nupload")
                .doesNotContain("__API_KEY__");
        assertThat(count(body, "name=\"api_key\"")).isEqualTo(1);
    }

    @Test
    void rejectsOversizedDownloadAndIncompleteUploadResponse() {
        StubTransport download = new StubTransport();
        download.respond(200, new byte[6]);
        assertThatThrownBy(() ->
                gateway(download).downloadOriginal(candidate(), 5))
                .isInstanceOf(MediaGatewayException.class)
                .extracting("code")
                .isEqualTo("CLOUDINARY_DOWNLOAD_TOO_LARGE");

        StubTransport upload = new StubTransport();
        upload.respond(200, "{}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() ->
                gateway(upload).publishNormalized(candidate(), jpeg()))
                .isInstanceOf(MediaGatewayException.class)
                .extracting("code")
                .isEqualTo("CLOUDINARY_RESPONSE_INVALID");
    }

    @Test
    void rejectsCloudinaryErrorStatusesAndInvalidConfiguration() {
        StubTransport download = new StubTransport();
        download.respond(403, new byte[0]);
        assertThatThrownBy(() ->
                gateway(download).downloadOriginal(candidate(), 5))
                .extracting("code")
                .isEqualTo("CLOUDINARY_DOWNLOAD_FAILED");

        StubTransport upload = new StubTransport();
        upload.respond(500, new byte[0]);
        assertThatThrownBy(() ->
                gateway(upload).publishNormalized(candidate(), jpeg()))
                .extracting("code")
                .isEqualTo("CLOUDINARY_NORMALIZE_FAILED");

        assertThatThrownBy(() -> new CloudinaryMediaProcessingGateway(
                new StubTransport(), new ObjectMapper(), "cloud", "api-key",
                testCredential(), fixedClock(), Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CloudinaryMediaProcessingGateway(
                new StubTransport(), new ObjectMapper(), null, "api-key",
                testCredential(), fixedClock(), Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectsPurposeSpecificOutputAndThreeVariants() {
        for (UploadPurpose purpose : UploadPurpose.values()) {
            StubTransport http = new StubTransport();
            String format = switch (purpose) {
                case AVATAR, TEAM_LOGO -> "png";
                case STORY_COVER, CHAPTER_IMAGE -> "jpg";
            };
            MediaProcessingOperations.Candidate candidate =
                    candidate(purpose);
            String publicId = "published/"
                    + candidate.ownerType().name().toLowerCase()
                    + "/owner/"
                    + purpose.name().toLowerCase()
                    + "/asset";
            http.respond(200, ("{\"public_id\":\"" + publicId
                    + "\",\"version\":7,\"format\":\"" + format
                    + "\",\"bytes\":20,\"eager\":[{},{},{}]}")
                    .getBytes(StandardCharsets.UTF_8));

            assertThat(gateway(http).publishNormalized(candidate, jpeg())
                    .format()).isEqualTo(format);
        }
    }

    private static CloudinaryMediaProcessingGateway gateway(
            StubTransport http
    ) {
        return new CloudinaryMediaProcessingGateway(
                http,
                new ObjectMapper(),
                "cloud",
                "api-key",
                testCredential(),
                fixedClock(),
                Duration.ofSeconds(5)
        );
    }

    private static String testCredential() {
        return String.join("", "unit-", "credential-", "only");
    }

    private static Clock fixedClock() {
        return Clock.fixed(
                Instant.parse("2026-07-24T00:00:00Z"),
                ZoneOffset.UTC
        );
    }

    private static MediaProcessingOperations.Candidate candidate() {
        return candidate(UploadPurpose.AVATAR);
    }

    private static MediaProcessingOperations.Candidate candidate(
            UploadPurpose purpose
    ) {
        MediaOwnerType ownerType = purpose == UploadPurpose.AVATAR
                ? MediaOwnerType.USER
                : MediaOwnerType.TEAM;
        return new MediaProcessingOperations.Candidate(
                "asset", "restricted/source", 42, "jpg", "a".repeat(64),
                4, 10, 10, ownerType, "owner",
                purpose, 1, Instant.MAX
        );
    }

    private static byte[] jpeg() {
        return new byte[]{
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01
        };
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length())
                / token.length();
    }

    private static final class StubTransport
            implements CloudinaryHttpTransport {

        private final ArrayDeque<StubResponse> responses = new ArrayDeque<>();
        private final java.util.ArrayList<StubRequest> requests =
                new java.util.ArrayList<>();

        void respond(int status, byte[] body) {
            responses.add(new StubResponse(status, body));
        }

        List<StubRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public Response get(
                String url,
                String accept,
                Duration timeout,
                int maximumResponseBytes
        ) {
            requests.add(new StubRequest("GET", url, accept, new byte[0]));
            StubResponse response = responses.removeFirst();
            return new Response(response.status(), response.body());
        }

        @Override
        public Response post(
                String url,
                String contentType,
                byte[] body,
                Duration timeout,
                int maximumResponseBytes
        ) {
            requests.add(new StubRequest("POST", url, contentType, body));
            StubResponse response = responses.removeFirst();
            return new Response(response.status(), response.body());
        }
    }

    private record StubRequest(
            String method,
            String url,
            String contentType,
            byte[] body
    ) {
    }

    private record StubResponse(int status, byte[] body) {
    }
}
