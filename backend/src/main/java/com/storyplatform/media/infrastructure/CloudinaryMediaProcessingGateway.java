package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.MediaGatewayException;
import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.application.port.MediaProcessingGateway;
import com.storyplatform.media.domain.UploadPurpose;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CloudinaryMediaProcessingGateway
        implements MediaProcessingGateway {

    private static final String MASTER_TRANSFORMATION =
            "c_limit,w_2400,h_2400,q_auto:good,fl_force_strip";
    private static final int MAXIMUM_RESPONSE_BYTES = 1_048_576;

    private final CloudinaryHttpTransport http;
    private final ObjectMapper mapper;
    private final CloudinaryUploadParameterSigner uploadSigner;
    private final byte[] apiSecret;
    private final String cloudName;
    private final String apiKey;
    private final Clock clock;
    private final Duration timeout;

    public CloudinaryMediaProcessingGateway(
            CloudinaryHttpTransport http,
            ObjectMapper mapper,
            String cloudName,
            String apiKey,
            String apiSecret,
            Clock clock,
            Duration timeout
    ) {
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.cloudName = token(cloudName, "cloudName");
        this.apiKey = token(apiKey, "apiKey");
        this.uploadSigner = new CloudinaryUploadParameterSigner(apiSecret);
        this.apiSecret = apiSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = Objects.requireNonNull(clock, "clock");
        if (timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(
                    "processing timeout must be between 1ms and 30s"
            );
        }
        this.timeout = timeout;
    }

    @Override
    public byte[] downloadOriginal(
            MediaProcessingOperations.Candidate candidate,
            long maximumBytes
    ) {
        String path = "v%d/%s.%s".formatted(
                candidate.cloudinaryVersion(),
                candidate.publicId(),
                candidate.format()
        );
        String signature = deliverySignature(path);
        String url = "https://res.cloudinary.com/%s/image/authenticated/"
                        .formatted(cloudName)
                        + "s--"
                        + signature
                        + "--/"
                        + path;
        try {
            CloudinaryHttpTransport.Response response = http.get(
                    url,
                    "image/*",
                    timeout,
                    Math.toIntExact(maximumBytes)
            );
            if (response.status() != 200) {
                throw failure(
                        "CLOUDINARY_DOWNLOAD_FAILED",
                        "Cloudinary returned a non-success download status.",
                        null
                );
            }
            if (response.body().length > maximumBytes) {
                throw failure(
                        "CLOUDINARY_DOWNLOAD_TOO_LARGE",
                        "Cloudinary source exceeded its declared limit.",
                        null
                );
            }
            return response.body();
        } catch (IOException exception) {
            throw failure(
                    "CLOUDINARY_IO_FAILURE",
                    "Unable to download the private Cloudinary source.",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(
                    "CLOUDINARY_INTERRUPTED",
                    "Cloudinary download was interrupted.",
                    exception
            );
        }
    }

    @Override
    public MediaProcessingOperations.PublishedAsset publishNormalized(
            MediaProcessingOperations.Candidate candidate,
            byte[] source
    ) {
        String normalizedId = normalizedPublicId(candidate);
        String outputFormat = outputFormat(candidate.purpose());
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(
                "context",
                "normalized_from_asset=" + candidate.assetId()
        );
        parameters.put("eager", variants(candidate.purpose()));
        parameters.put("eager_async", "false");
        parameters.put("format", outputFormat);
        parameters.put("overwrite", "false");
        parameters.put("public_id", normalizedId);
        parameters.put("timestamp", Long.toString(
                clock.instant().getEpochSecond()
        ));
        parameters.put("transformation", MASTER_TRANSFORMATION);
        parameters.put("type", "upload");
        parameters.put("unique_filename", "false");
        parameters.put("use_filename", "false");
        String apiSignature = uploadSigner.sign(parameters);

        String boundary = "gioitruyen-" + UUID.randomUUID();
        byte[] requestBody = multipart(
                boundary,
                parameters,
                apiKey,
                apiSignature,
                source,
                candidate.format()
        );
        try {
            CloudinaryHttpTransport.Response response = http.post(
                    "https://api.cloudinary.com/v1_1/%s/image/upload"
                            .formatted(cloudName),
                    "multipart/form-data; boundary=" + boundary,
                    requestBody,
                    timeout,
                    MAXIMUM_RESPONSE_BYTES
            );
            if (response.status() < 200
                    || response.status() >= 300) {
                throw failure(
                        "CLOUDINARY_NORMALIZE_FAILED",
                        "Cloudinary rejected normalized media.",
                        null
                );
            }
            if (response.body().length > MAXIMUM_RESPONSE_BYTES) {
                throw failure(
                        "CLOUDINARY_RESPONSE_TOO_LARGE",
                        "Cloudinary response exceeded the safe limit.",
                        null
                );
            }
            return published(
                    response.body(),
                    normalizedId,
                    outputFormat
            );
        } catch (IOException exception) {
            throw failure(
                    "CLOUDINARY_IO_FAILURE",
                    "Unable to publish normalized Cloudinary media.",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(
                    "CLOUDINARY_INTERRUPTED",
                    "Cloudinary normalization was interrupted.",
                    exception
            );
        }
    }

    private MediaProcessingOperations.PublishedAsset published(
            byte[] body,
            String expectedPublicId,
            String expectedFormat
    ) {
        try {
            JsonNode root = mapper.readTree(body);
            String publicId = root.path("public_id").asText("");
            String format = root.path("format").asText("");
            long version = root.path("version").asLong(-1);
            long bytes = root.path("bytes").asLong(-1);
            int eagerCount = root.path("eager").size();
            if (!expectedPublicId.equals(publicId)
                    || !expectedFormat.equals(format)
                    || version < 1
                    || bytes < 1
                    || eagerCount != 3) {
                throw failure(
                        "CLOUDINARY_RESPONSE_INVALID",
                        "Cloudinary normalization response is incomplete.",
                        null
                );
            }
            return new MediaProcessingOperations.PublishedAsset(
                    publicId,
                    version,
                    format,
                    bytes
            );
        } catch (MediaGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                    "CLOUDINARY_RESPONSE_INVALID",
                    "Cloudinary normalization response is malformed.",
                    exception
            );
        }
    }

    private String deliverySignature(String path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(apiSecret);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash)
                    .substring(0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] multipart(
            String boundary,
            Map<String, String> parameters,
            String apiKey,
            String signature,
            byte[] source,
            String format
    ) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            parameters.forEach((name, value) ->
                    field(output, boundary, name, value));
            field(output, boundary, "api_key", apiKey);
            field(output, boundary, "signature", signature);
            byte[] marker = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; "
                    + "filename=\"source." + format + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            output.write(marker);
            output.write(source);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(("--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to build multipart body",
                    exception
            );
        }
    }

    private static void field(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String value
    ) {
        try {
            output.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\""
                    + name
                    + "\"\r\n\r\n"
                    + value
                    + "\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to build multipart field",
                    exception
            );
        }
    }

    private static String normalizedPublicId(
            MediaProcessingOperations.Candidate candidate
    ) {
        return "published/%s/%s/%s/%s".formatted(
                candidate.ownerType().name().toLowerCase(
                        java.util.Locale.ROOT
                ),
                candidate.ownerId(),
                candidate.purpose().name().toLowerCase(
                        java.util.Locale.ROOT
                ),
                candidate.assetId()
        );
    }

    private static String outputFormat(UploadPurpose purpose) {
        return switch (purpose) {
            case AVATAR, TEAM_LOGO -> "png";
            case STORY_COVER, CHAPTER_IMAGE -> "jpg";
        };
    }

    private static String variants(UploadPurpose purpose) {
        return switch (purpose) {
            case AVATAR, TEAM_LOGO ->
                    "c_fill,g_auto,w_128,h_128,q_auto:eco,f_webp"
                            + "|c_fill,g_auto,w_256,h_256,q_auto:eco,f_webp"
                            + "|c_fill,g_auto,w_512,h_512,q_auto:good,f_webp";
            case STORY_COVER ->
                    "c_fill,g_auto,w_320,h_480,q_auto:eco,f_webp"
                            + "|c_fill,g_auto,w_640,h_960,q_auto:good,f_webp"
                            + "|c_fill,g_auto,w_960,h_1440,q_auto:good,f_webp";
            case CHAPTER_IMAGE ->
                    "c_limit,w_640,q_auto:eco,f_webp"
                            + "|c_limit,w_1280,q_auto:good,f_webp"
                            + "|c_limit,w_1920,q_auto:good,f_webp";
        };
    }

    private static String token(String value, String field) {
        if (value == null
                || !value.matches("[A-Za-z0-9_-]{2,128}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static MediaGatewayException failure(
            String code,
            String message,
            Throwable cause
    ) {
        return new MediaGatewayException(code, message, cause);
    }
}
