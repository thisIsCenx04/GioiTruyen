package com.storyplatform.media.api;

import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.UploadSignatureOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@ConditionalOnProperty(
        name = "app.media.cloudinary.enabled",
        havingValue = "true"
)
public final class UploadSignatureController {

    private final UploadSignatureOperations signatures;

    public UploadSignatureController(
            UploadSignatureOperations signatures
    ) {
        this.signatures = Objects.requireNonNull(
                signatures,
                "signatures"
        );
    }

    @PostMapping("/media/upload-signatures")
    public ResponseEntity<UploadSignatureOperations.UploadGrant> issue(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UploadSignatureRequest request
    ) {
        try {
            UploadSignatureOperations.UploadGrant grant = signatures.issue(
                    jwt.getSubject(),
                    new UploadSignatureOperations.UploadCommand(
                            request.ownerType(),
                            request.ownerId(),
                            request.purpose(),
                            request.contentType(),
                            request.sizeBytes(),
                            request.sha256()
                    )
            );
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(grant);
        } catch (MediaRequestException exception) {
            throw new ApiException(
                    exception.forbidden()
                            ? HttpStatus.FORBIDDEN
                            : HttpStatus.BAD_REQUEST,
                    exception.code(),
                    "Media upload request rejected",
                    exception.getMessage()
            );
        }
    }
}
