package com.storyplatform.media.application;

import com.storyplatform.media.application.port.UploadParameterSigner;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class UploadSignatureUseCase
        implements UploadSignatureOperations {

    static final Duration MAXIMUM_SIGNATURE_TTL = Duration.ofHours(1);
    private final TeamPermissionAuthorizer teams;
    private final UploadParameterSigner signer;
    private final Supplier<String> intentIds;
    private final Clock clock;
    private final Duration ttl;
    private final String cloudName;
    private final String apiKey;
    private final String uploadPreset;
    private final String rootFolder;

    public UploadSignatureUseCase(
            TeamPermissionAuthorizer teams,
            UploadParameterSigner signer,
            Supplier<String> intentIds,
            Clock clock,
            Duration ttl,
            String cloudName,
            String apiKey,
            String uploadPreset,
            String rootFolder
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.intentIds = Objects.requireNonNull(intentIds, "intentIds");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = requireTtl(ttl);
        this.cloudName = token(cloudName, "cloudName");
        this.apiKey = token(apiKey, "apiKey");
        this.uploadPreset = token(uploadPreset, "uploadPreset");
        this.rootFolder = folder(rootFolder);
    }

    @Override
    public UploadGrant issue(String actorId, UploadCommand command) {
        String actor = uuid(actorId, "actorId");
        Objects.requireNonNull(command, "command");
        MediaOwnerType ownerType = Objects.requireNonNull(
                command.ownerType(),
                "ownerType"
        );
        UploadPurpose purpose = Objects.requireNonNull(
                command.purpose(),
                "purpose"
        );
        String ownerId = uuid(command.ownerId(), "ownerId");
        authorize(actor, ownerType, ownerId, purpose);

        String contentType = contentType(command.contentType(), purpose);
        long sizeBytes = size(command.sizeBytes(), purpose);
        String sha256 = sha256(command.sha256());
        String intentId = uuid(intentIds.get(), "intentId");
        Instant issuedAt = clock.instant();

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(
                "asset_folder",
                assetFolder(ownerType, ownerId, purpose)
        );
        parameters.put(
                "context",
                context(
                        intentId,
                        ownerType,
                        ownerId,
                        purpose,
                        contentType,
                        sizeBytes,
                        sha256
                )
        );
        parameters.put("overwrite", "false");
        parameters.put("public_id", intentId);
        parameters.put("timestamp", Long.toString(issuedAt.getEpochSecond()));
        parameters.put("unique_filename", "false");
        parameters.put("upload_preset", uploadPreset);
        parameters.put("use_filename", "false");

        Map<String, String> signedParameters = Map.copyOf(parameters);
        return new UploadGrant(
                intentId,
                "https://api.cloudinary.com/v1_1/%s/image/authenticated"
                        .formatted(cloudName),
                apiKey,
                signer.sign(signedParameters),
                issuedAt.plus(ttl),
                purpose.maximumBytes(),
                purpose.allowedContentTypes(),
                signedParameters
        );
    }

    private void authorize(
            String actorId,
            MediaOwnerType ownerType,
            String ownerId,
            UploadPurpose purpose
    ) {
        if (purpose.ownerType() != ownerType) {
            throw rejected(
                    "MEDIA_PURPOSE_OWNER_INVALID",
                    "The upload purpose does not support this owner type."
            );
        }
        boolean allowed = ownerType == MediaOwnerType.USER
                ? actorId.equals(ownerId)
                : teams.allows(actorId, ownerId, purpose.permission());
        if (!allowed) {
            throw new MediaRequestException(
                    "MEDIA_OWNER_FORBIDDEN",
                    "The authenticated user cannot upload for this owner.",
                    true
            );
        }
    }

    private String assetFolder(
            MediaOwnerType ownerType,
            String ownerId,
            UploadPurpose purpose
    ) {
        return "%s/%s/%s/%s".formatted(
                rootFolder,
                ownerType.name().toLowerCase(Locale.ROOT),
                ownerId,
                purpose.name().toLowerCase(Locale.ROOT)
        );
    }

    private static String context(
            String intentId,
            MediaOwnerType ownerType,
            String ownerId,
            UploadPurpose purpose,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
        return String.join(
                "|",
                "declared_bytes=" + sizeBytes,
                "declared_content_type=" + contentType,
                "declared_sha256=" + sha256,
                "intent_id=" + intentId,
                "owner_id=" + ownerId,
                "owner_type=" + ownerType.name().toLowerCase(Locale.ROOT),
                "purpose=" + purpose.name().toLowerCase(Locale.ROOT)
        );
    }

    private static Duration requireTtl(Duration value) {
        if (value == null
                || value.compareTo(Duration.ofMinutes(1)) < 0
                || value.compareTo(MAXIMUM_SIGNATURE_TTL) > 0) {
            throw new IllegalArgumentException(
                    "signature TTL must be between 1 minute and 1 hour"
            );
        }
        return value;
    }

    private static String token(String value, String field) {
        if (value == null
                || !value.matches("[A-Za-z0-9_-]{2,128}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String folder(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rootFolder is invalid");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        boolean valid = normalized.length() <= 128
                && normalized.matches("[a-z0-9_-]+(?:/[a-z0-9_-]+)*")
                && Arrays.stream(normalized.split("/"))
                .allMatch(segment -> segment.length() <= 64)
                && !normalized.contains("..");
        if (!valid) {
            throw new IllegalArgumentException("rootFolder is invalid");
        }
        return normalized;
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw rejected(
                    "MEDIA_IDENTIFIER_INVALID",
                    field + " must be a canonical UUID."
            );
        }
    }

    private static String contentType(
            String value,
            UploadPurpose purpose
    ) {
        String normalized = value == null
                ? ""
                : value.strip().toLowerCase(Locale.ROOT);
        if (!purpose.allowedContentTypes().contains(normalized)) {
            throw rejected(
                    "MEDIA_CONTENT_TYPE_INVALID",
                    "The declared content type is not allowed."
            );
        }
        return normalized;
    }

    private static long size(long value, UploadPurpose purpose) {
        if (value < 1 || value > purpose.maximumBytes()) {
            throw rejected(
                    "MEDIA_SIZE_INVALID",
                    "The declared size exceeds the purpose limit."
            );
        }
        return value;
    }

    private static String sha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw rejected(
                    "MEDIA_HASH_INVALID",
                    "sha256 must be a lowercase hexadecimal digest."
            );
        }
        return value;
    }

    private static MediaRequestException rejected(
            String code,
            String message
    ) {
        return new MediaRequestException(code, message, false);
    }
}
