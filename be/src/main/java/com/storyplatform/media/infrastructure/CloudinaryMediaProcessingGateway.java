package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaGatewayException;
import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.domain.UploadPurpose;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

/**
 * Cloudinary-backed implementation of the media processing gateway. Handles
 * authenticated downloads and signed normalised uploads.
 */
public final class CloudinaryMediaProcessingGateway {

    private static final String DOWNLOAD_ACCEPT = "application/octet-stream";
    private static final String UPLOAD_URL_TEMPLATE =
            "https://api.cloudinary.com/v1_1/%s/image/upload";
    private static final String DELIVERY_BASE =
            "https://res.cloudinary.com/%s/image/authenticated";
    private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;
    private static final int MAX_UPLOAD_RESPONSE_BYTES = 1024 * 1024;
    private static final String HMAC_ALGO = "HmacSHA256";

    private final CloudinaryHttpTransport http;
    private final ObjectMapper mapper;
    private final String cloudName;
    private final String apiKey;
    private final String credential;
    private final Clock clock;
    private final Duration timeout;

    public CloudinaryMediaProcessingGateway(
            CloudinaryHttpTransport http,
            ObjectMapper mapper,
            String cloudName,
            String apiKey,
            String credential,
            Clock clock,
            Duration timeout
    ) {
        Objects.requireNonNull(http, "http");
        Objects.requireNonNull(mapper, "mapper");
        if (cloudName == null || cloudName.isBlank()) {
            throw new IllegalArgumentException("cloudName must not be blank");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.http = http;
        this.mapper = mapper;
        this.cloudName = cloudName;
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.credential = Objects.requireNonNull(credential, "credential");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeout = timeout;
    }

    /**
     * Downloads the original (authenticated) asset from Cloudinary.
     *
     * @param candidate the processing candidate
     * @param maxBytes  the maximum allowed response size
     * @return the raw bytes of the original asset
     */
    public byte[] downloadOriginal(
            MediaProcessingOperations.Candidate candidate,
            int maxBytes
    ) {
        String signedUrl = buildSignedDeliveryUrl(candidate);
        CloudinaryHttpTransport.Response response = http.get(
                signedUrl,
                DOWNLOAD_ACCEPT,
                timeout,
                maxBytes + 1
        );
        if (response.status() != 200) {
            throw new MediaGatewayException(
                    "CLOUDINARY_DOWNLOAD_FAILED",
                    "Cloudinary download failed with status " + response.status(),
                    null
            );
        }
        if (response.body().length > maxBytes) {
            throw new MediaGatewayException(
                    "CLOUDINARY_DOWNLOAD_TOO_LARGE",
                    "Downloaded asset exceeds the maximum allowed size of "
                            + maxBytes + " bytes",
                    null
            );
        }
        return response.body();
    }

    /**
     * Uploads a normalised asset to Cloudinary and returns the published asset
     * metadata.
     *
     * @param candidate the processing candidate
     * @param imageData the normalised image bytes to upload
     * @return the published asset details
     */
    public MediaProcessingOperations.PublishedAsset publishNormalized(
            MediaProcessingOperations.Candidate candidate,
            byte[] imageData
    ) {
        long timestamp = clock.instant().getEpochSecond();
        String format = outputFormat(candidate.purpose());
        String publicId = buildPublicId(candidate);
        String transformation = buildTransformation(candidate.purpose());

        Map<String, String> params = new TreeMap<>();
        params.put("api_key", apiKey);
        params.put("eager", transformation);
        params.put("format", format);
        params.put("public_id", publicId);
        params.put("timestamp", String.valueOf(timestamp));
        params.put("type", "upload");

        String signature = sign(params);
        params.put("signature", signature);

        byte[] multipart = buildMultipartBody(params, imageData);
        String boundary = "boundary-" + timestamp;
        String uploadUrl = String.format(UPLOAD_URL_TEMPLATE, cloudName);

        CloudinaryHttpTransport.Response response = http.post(
                uploadUrl,
                "multipart/form-data; boundary=" + boundary,
                multipart,
                timeout,
                MAX_UPLOAD_RESPONSE_BYTES
        );
        if (response.status() != 200) {
            throw new MediaGatewayException(
                    "CLOUDINARY_NORMALIZE_FAILED",
                    "Cloudinary upload failed with status " + response.status(),
                    null
            );
        }
        return parsePublishedAsset(response.body());
    }

    // --- private helpers ---

    private String buildSignedDeliveryUrl(
            MediaProcessingOperations.Candidate candidate
    ) {
        long version = candidate.cloudinaryVersion();
        String publicId = candidate.publicId();
        String toSign = "s--" + shortSignature(version + "/" + publicId) + "--";
        return String.format(
                "%s/%s/v%d/%s.%s",
                String.format(DELIVERY_BASE, cloudName),
                toSign,
                version,
                publicId,
                candidate.format()
        );
    }

    private String shortSignature(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(
                    credential.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGO
            ));
            byte[] digest = mac.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest)
                    .substring(0, 8);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private String sign(Map<String, String> params) {
        // Build canonical string: param=value pairs sorted, excluding signature
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!"signature".equals(k) && !"api_key".equals(k)) {
                if (!sb.isEmpty()) sb.append("&");
                sb.append(k).append("=").append(v);
            }
        });
        sb.append(credential);
        try {
            java.security.MessageDigest sha =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(
                    sb.toString().getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String outputFormat(UploadPurpose purpose) {
        return switch (purpose) {
            case AVATAR, TEAM_LOGO -> "png";
            case STORY_COVER, CHAPTER_IMAGE -> "jpg";
        };
    }

    private static String buildPublicId(
            MediaProcessingOperations.Candidate candidate
    ) {
        return "published/"
                + candidate.ownerType().name().toLowerCase()
                + "/" + candidate.ownerId()
                + "/" + candidate.purpose().name().toLowerCase()
                + "/" + candidate.assetId();
    }

    private static String buildTransformation(UploadPurpose purpose) {
        return switch (purpose) {
            case AVATAR -> "w_512,h_512,c_fill|w_256,h_256,c_fill|w_128,h_128,c_fill"
                    + "|fl_force_strip";
            case TEAM_LOGO -> "w_256,h_256,c_pad|w_128,h_128,c_pad|w_64,h_64,c_pad"
                    + "|fl_force_strip";
            case STORY_COVER -> "w_800,h_1120,c_fill|w_400,h_560,c_fill|w_200,h_280,c_fill"
                    + "|fl_force_strip";
            case CHAPTER_IMAGE -> "w_1200,h_630,c_fill|w_600,h_315,c_fill|w_300,h_158,c_fill"
                    + "|fl_force_strip";
        };
    }

    private static byte[] buildMultipartBody(
            Map<String, String> params,
            byte[] imageData
    ) {
        // Simplified multipart/form-data builder for text fields + file
        try {
            String boundary = "---boundary";
            var out = new ByteArrayOutputStream();
            byte[] crlf = "\r\n".getBytes(StandardCharsets.ISO_8859_1);
            for (Map.Entry<String, String> entry : params.entrySet()) {
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(("Content-Disposition: form-data; name=\""
                        + entry.getKey() + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
                out.write(entry.getValue().getBytes(StandardCharsets.ISO_8859_1));
                out.write(crlf);
            }
            // Image field
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write("Content-Disposition: form-data; name=\"file\"; filename=\"upload\"\r\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write("Content-Type: application/octet-stream\r\n\r\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(imageData);
            out.write(crlf);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build multipart body", e);
        }
    }

    private MediaProcessingOperations.PublishedAsset parsePublishedAsset(
            byte[] body
    ) {
        try {
            JsonNode root = mapper.readTree(body);
            String publicId = Optional.ofNullable(root.path("public_id").textValue())
                    .orElseThrow(() -> new MediaGatewayException(
                            "CLOUDINARY_RESPONSE_INVALID",
                            "Missing public_id in Cloudinary upload response",
                            null
                    ));
            long version = root.path("version").longValue();
            String format = root.path("format").textValue();
            long bytes = root.path("bytes").longValue();
            JsonNode eager = root.path("eager");
            if (eager.isMissingNode() || !eager.isArray() || eager.isEmpty()) {
                throw new MediaGatewayException(
                        "CLOUDINARY_RESPONSE_INVALID",
                        "Missing or empty eager transformations in Cloudinary response",
                        null
                );
            }
            return new MediaProcessingOperations.PublishedAsset(
                    publicId, version, format, bytes
            );
        } catch (MediaGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new MediaGatewayException(
                    "CLOUDINARY_RESPONSE_INVALID",
                    "Failed to parse Cloudinary upload response",
                    e
            );
        }
    }
}
