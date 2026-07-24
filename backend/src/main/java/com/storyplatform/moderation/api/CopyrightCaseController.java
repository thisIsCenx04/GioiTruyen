package com.storyplatform.moderation.api;

import com.storyplatform.moderation.application.CopyrightCaseException;
import com.storyplatform.moderation.application.CopyrightCaseOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
public final class CopyrightCaseController {

    private final CopyrightCaseOperations cases;
    private final JwtPrivilegeEvaluator privileges;

    public CopyrightCaseController(
            CopyrightCaseOperations cases,
            JwtPrivilegeEvaluator privileges
    ) {
        this.cases = Objects.requireNonNull(cases, "cases");
        this.privileges = Objects.requireNonNull(
                privileges,
                "privileges"
        );
    }

    @PostMapping("/copyright/cases")
    public ResponseEntity<CopyrightCaseOperations.CopyrightCaseView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCopyrightCaseRequest request
    ) {
        try {
            var created = cases.create(
                    jwt.getSubject(),
                    new CopyrightCaseOperations.CreateCopyrightCase(
                            request.storyId(),
                            request.claimantName(),
                            request.statement(),
                            request.evidenceMediaIds()
                    )
            );
            return ResponseEntity.created(
                            URI.create("/copyright/cases/" + created.id())
                    )
                    .cacheControl(CacheControl.noStore())
                    .body(created);
        } catch (CopyrightCaseException exception) {
            throw problem(exception);
        }
    }

    @PostMapping("/copyright/cases/{caseId}/appeals")
    public ResponseEntity<CopyrightCaseOperations.CopyrightCaseView> appeal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String caseId,
            @Valid @RequestBody CopyrightAppealRequest request
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(cases.appeal(
                            jwt.getSubject(),
                            caseId,
                            request.statement()
                    ));
        } catch (CopyrightCaseException exception) {
            throw problem(exception);
        }
    }

    @PostMapping("/copyright/cases/{caseId}/decisions")
    public ResponseEntity<CopyrightCaseOperations.CopyrightCaseView> decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String caseId,
            @Valid @RequestBody CopyrightDecisionRequest request
    ) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.MODERATION_DECIDE
        )) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MODERATION_FORBIDDEN",
                    "Copyright decision rejected",
                    "Moderator privileges are required."
            );
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(cases.decide(
                            jwt.getSubject(),
                            caseId,
                            request.decision(),
                            request.reasonCode(),
                            request.note()
                    ));
        } catch (CopyrightCaseException exception) {
            throw problem(exception);
        }
    }

    private static ApiException problem(CopyrightCaseException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Copyright request rejected",
                exception.getMessage()
        );
    }

    public record CreateCopyrightCaseRequest(
            @NotBlank String storyId,
            @NotBlank @Size(max = 160) String claimantName,
            @NotBlank @Size(max = 5000) String statement,
            @NotEmpty @Size(max = 10) List<@NotBlank String> evidenceMediaIds
    ) {
        public CreateCopyrightCaseRequest {
            evidenceMediaIds = evidenceMediaIds == null
                    ? List.of()
                    : List.copyOf(evidenceMediaIds);
        }
    }

    public record CopyrightAppealRequest(
            @NotBlank @Size(max = 4000) String statement
    ) {
    }

    public record CopyrightDecisionRequest(
            @NotNull CopyrightCaseOperations.Decision decision,
            @NotBlank @Size(max = 64) String reasonCode,
            @NotBlank @Size(max = 2000) String note
    ) {
    }
}
