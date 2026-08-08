package com.storyplatform.unit.media.application;

import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.UploadSignatureOperations;
import com.storyplatform.media.application.UploadSignatureUseCase;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadSignatureUseCaseTest {

    private static final String USER_ID =
            "00000000-0000-4000-8000-000000000001";
    private static final String TEAM_ID =
            "00000000-0000-4000-8000-000000000002";
    private static final String INTENT_ID =
            "00000000-0000-4000-8000-000000000003";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void bindsOwnerPurposeTypeSizeHashFolderAndExpiry() {
        AtomicReference<Map<String, String>> signed =
                new AtomicReference<>();
        UploadSignatureUseCase useCase = useCase(
                (userId, teamId, permission) -> false,
                parameters -> {
                    signed.set(parameters);
                    return "signed-value";
                },
                Duration.ofMinutes(10)
        );

        UploadSignatureOperations.UploadGrant grant = useCase.issue(
                USER_ID,
                new UploadSignatureOperations.UploadCommand(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.AVATAR,
                        "IMAGE/PNG",
                        2048,
                        HASH
                )
        );

        assertThat(grant.expiresAt()).isEqualTo(
                NOW.plus(Duration.ofMinutes(10))
        );
        assertThat(grant.signature()).isEqualTo("signed-value");
        assertThat(grant.uploadUrl()).endsWith("/image/authenticated");
        assertThat(grant.maximumBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(signed.get())
                .containsEntry(
                        "asset_folder",
                        "gioitruyen/test/user/" + USER_ID + "/avatar"
                )
                .containsEntry("public_id", INTENT_ID)
                .containsEntry("timestamp", "1784851200")
                .containsEntry("upload_preset", "restricted_upload");
        assertThat(signed.get().get("context"))
                .contains("intent_id=" + INTENT_ID)
                .contains("owner_id=" + USER_ID)
                .contains("purpose=avatar")
                .contains("declared_content_type=image/png")
                .contains("declared_bytes=2048")
                .contains("declared_sha256=" + HASH);
    }

    @Test
    void teamUploadRequiresThePurposePermission() {
        TeamPermissionAuthorizer authorizer =
                (userId, teamId, permission) ->
                        USER_ID.equals(userId)
                                && TEAM_ID.equals(teamId)
                                && "story:edit".equals(permission);
        UploadSignatureUseCase useCase = useCase(
                authorizer,
                parameters -> "signature",
                Duration.ofMinutes(20)
        );

        assertThat(useCase.issue(
                USER_ID,
                command(
                        MediaOwnerType.TEAM,
                        TEAM_ID,
                        UploadPurpose.STORY_COVER,
                        4096
                )
        ).parameters().get("context")).contains("purpose=story_cover");

        assertThatThrownBy(() -> useCase.issue(
                USER_ID,
                command(
                        MediaOwnerType.TEAM,
                        TEAM_ID,
                        UploadPurpose.TEAM_LOGO,
                        4096
                )
        ))
                .isInstanceOf(MediaRequestException.class)
                .extracting("code")
                .isEqualTo("MEDIA_OWNER_FORBIDDEN");
    }

    @Test
    void rejectsTamperedPurposeTypeSizeAndHashBeforeSigning() {
        UploadSignatureUseCase useCase = useCase(
                (userId, teamId, permission) -> true,
                parameters -> "signature",
                Duration.ofMinutes(10)
        );

        assertRejected(
                useCase,
                command(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.STORY_COVER,
                        100
                ),
                "MEDIA_PURPOSE_OWNER_INVALID"
        );
        assertRejected(
                useCase,
                new UploadSignatureOperations.UploadCommand(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.AVATAR,
                        "image/svg+xml",
                        100,
                        HASH
                ),
                "MEDIA_CONTENT_TYPE_INVALID"
        );
        assertRejected(
                useCase,
                command(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.AVATAR,
                        5L * 1024 * 1024 + 1
                ),
                "MEDIA_SIZE_INVALID"
        );
        assertRejected(
                useCase,
                new UploadSignatureOperations.UploadCommand(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.AVATAR,
                        "image/jpeg",
                        100,
                        "A".repeat(64)
                ),
                "MEDIA_HASH_INVALID"
        );
    }

    @Test
    void rejectsTtlBeyondCloudinarySignatureWindow() {
        assertThatThrownBy(() -> useCase(
                (userId, teamId, permission) -> true,
                parameters -> "signature",
                Duration.ofHours(1).plusSeconds(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 minute and 1 hour");
    }

    @Test
    void rejectsUnsafeConfigurationAtStartup() {
        assertConfigurationRejected(
                null,
                "demo-cloud",
                "gioitruyen/test",
                "signature TTL"
        );
        assertConfigurationRejected(
                Duration.ofSeconds(59),
                "demo-cloud",
                "gioitruyen/test",
                "signature TTL"
        );
        assertConfigurationRejected(
                Duration.ofMinutes(10),
                null,
                "gioitruyen/test",
                "cloudName"
        );
        assertConfigurationRejected(
                Duration.ofMinutes(10),
                "demo-cloud",
                "../private",
                "rootFolder"
        );
        assertConfigurationRejected(
                Duration.ofMinutes(10),
                "demo-cloud",
                "a".repeat(65) + "/test",
                "rootFolder"
        );
    }

    @Test
    void rejectsNullAndLowerBoundaryDeclarations() {
        UploadSignatureUseCase useCase = useCase(
                (userId, teamId, permission) -> true,
                parameters -> "signature",
                Duration.ofMinutes(10)
        );
        assertRejected(
                useCase,
                new UploadSignatureOperations.UploadCommand(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.AVATAR,
                        null,
                        100,
                        HASH
                ),
                "MEDIA_CONTENT_TYPE_INVALID"
        );
        assertRejected(
                useCase,
                command(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.AVATAR,
                        0
                ),
                "MEDIA_SIZE_INVALID"
        );
        assertRejected(
                useCase,
                new UploadSignatureOperations.UploadCommand(
                        MediaOwnerType.USER,
                        USER_ID,
                        UploadPurpose.AVATAR,
                        "image/jpeg",
                        100,
                        null
                ),
                "MEDIA_HASH_INVALID"
        );
    }

    private static UploadSignatureUseCase useCase(
            TeamPermissionAuthorizer teams,
            com.storyplatform.media.application.port.UploadParameterSigner
                    signer,
            Duration ttl
    ) {
        return new UploadSignatureUseCase(
                teams,
                signer,
                () -> INTENT_ID,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ttl,
                "demo-cloud",
                "api_key_123",
                "restricted_upload",
                "gioitruyen/test"
        );
    }

    private static void assertConfigurationRejected(
            Duration ttl,
            String cloudName,
            String rootFolder,
            String message
    ) {
        assertThatThrownBy(() -> new UploadSignatureUseCase(
                (userId, teamId, permission) -> true,
                parameters -> "signature",
                () -> INTENT_ID,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ttl,
                cloudName,
                "api_key_123",
                "restricted_upload",
                rootFolder
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private static UploadSignatureOperations.UploadCommand command(
            MediaOwnerType ownerType,
            String ownerId,
            UploadPurpose purpose,
            long size
    ) {
        return new UploadSignatureOperations.UploadCommand(
                ownerType,
                ownerId,
                purpose,
                "image/jpeg",
                size,
                HASH
        );
    }

    private static void assertRejected(
            UploadSignatureUseCase useCase,
            UploadSignatureOperations.UploadCommand command,
            String code
    ) {
        assertThatThrownBy(() -> useCase.issue(USER_ID, command))
                .isInstanceOf(MediaRequestException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
