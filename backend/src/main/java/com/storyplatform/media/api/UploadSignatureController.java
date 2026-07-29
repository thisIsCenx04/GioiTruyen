package com.storyplatform.media.api;

import com.storyplatform.media.application.MediaGatewayException;
import com.storyplatform.media.application.MediaPolicyException;
import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.UploadSignatureOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Objects;

@RestController
public final class UploadSignatureController {

    private final Optional<UploadSignatureOperations> signatures;

    public UploadSignatureController(
            Optional<UploadSignatureOperations> signatures
    ) {
        this.signatures = Objects.requireNonNull(signatures, "signatures");
    }

    @PostMapping(
            path = "/media/upload-signatures",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UploadSignatureOperations.UploadGrant> issue(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UploadSignatureRequest request
    ) {
        try {
            UploadSignatureOperations operations = signatures.orElseThrow(() ->
                    problem(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "MEDIA_UPLOAD_UNAVAILABLE",
                            "Upload signing is not configured."
                    )
            );
            var grant = operations.issue(
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
            throw problem(
                    exception.forbidden()
                            ? HttpStatus.FORBIDDEN
                            : HttpStatus.BAD_REQUEST,
                    exception.code(),
                    exception.getMessage()
            );
        } catch (MediaPolicyException exception) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    exception.code(),
                    exception.getMessage()
            );
        } catch (MediaGatewayException exception) {
            throw problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception.code(),
                    exception.getMessage()
            );
        }
    }

    private static ApiException problem(
            HttpStatus status,
            String code,
            String detail
    ) {
        return new ApiException(
                status,
                code,
                "Upload signature request rejected",
                detail
        );
    }
}
