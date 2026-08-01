package com.storyplatform.admin.api;

import com.storyplatform.admin.application.AdminContentService;
import com.storyplatform.admin.application.AdminContentService.CategoryCommand;
import com.storyplatform.admin.application.AdminContentService.LedgerCommand;
import com.storyplatform.admin.application.AdminContentService.StoryCommand;
import com.storyplatform.admin.application.AdminContentService.TeamCommand;
import com.storyplatform.admin.application.AdminContentService.UserCommand;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/admin")
public class AdminContentController {

    private static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";
    private static final String UUID_PATTERN = "[0-9a-fA-F-]{36}";
    private static final String STATUS_PATTERN = "[A-Z_]{3,40}";

    private final AdminContentService content;
    private final JwtPrivilegeEvaluator privileges;

    public AdminContentController(
            AdminContentService content,
            JwtPrivilegeEvaluator privileges
    ) {
        this.content = Objects.requireNonNull(content, "content");
        this.privileges = Objects.requireNonNull(privileges, "privileges");
    }

    @PostMapping("/content/stories")
    public Map<String, String> createStory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StoryRequest request
    ) {
        requireAdmin(jwt);
        return Map.of("id", content.createStory(request.command()));
    }

    @PutMapping("/content/stories/{id:" + UUID_PATTERN + "}")
    public void updateStory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody StoryRequest request
    ) {
        requireAdmin(jwt);
        content.updateStory(id, request.command());
    }

    @DeleteMapping("/content/stories/{id:" + UUID_PATTERN + "}")
    public void archiveStory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id
    ) {
        requireAdmin(jwt);
        content.archiveStory(id);
    }

    @PostMapping("/content/categories")
    public Map<String, String> createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CategoryRequest request
    ) {
        requireAdmin(jwt);
        return Map.of("id", content.createCategory(request.command()));
    }

    @PutMapping("/content/categories/{id:" + UUID_PATTERN + "}")
    public void updateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody CategoryRequest request
    ) {
        requireAdmin(jwt);
        content.updateCategory(id, request.command());
    }

    @DeleteMapping("/content/categories/{id:" + UUID_PATTERN + "}")
    public void archiveCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id
    ) {
        requireAdmin(jwt);
        content.archiveCategory(id);
    }

    @PostMapping("/content/teams")
    public Map<String, String> createTeam(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TeamRequest request
    ) {
        requireAdmin(jwt);
        return Map.of("id", content.createTeam(request.command()));
    }

    @PutMapping("/content/teams/{id:" + UUID_PATTERN + "}")
    public void updateTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody TeamRequest request
    ) {
        requireAdmin(jwt);
        content.updateTeam(id, request.command());
    }

    @DeleteMapping("/content/teams/{id:" + UUID_PATTERN + "}")
    public void archiveTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id
    ) {
        requireAdmin(jwt);
        content.archiveTeam(id);
    }

    @PostMapping("/content/users")
    public Map<String, String> createUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserCreateRequest request
    ) {
        requireAdmin(jwt);
        return Map.of("id", content.createUser(request.command()));
    }

    @PutMapping("/content/users/{id:" + UUID_PATTERN + "}")
    public void updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        requireAdmin(jwt);
        content.updateUser(id, request.command());
    }

    @DeleteMapping("/content/users/{id:" + UUID_PATTERN + "}")
    public void archiveUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id
    ) {
        requireAdmin(jwt);
        content.archiveUser(id);
    }

    @PostMapping("/finance/cash-flow")
    public Map<String, String> createLedgerEntry(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LedgerRequest request
    ) {
        requireAdmin(jwt);
        return Map.of("id", content.createLedgerEntry(request.command()));
    }

    @PostMapping("/finance/cash-flow/{id:" + UUID_PATTERN + "}/reverse")
    public Map<String, String> reverseLedgerEntry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody ReversalRequest request
    ) {
        requireAdmin(jwt);
        return Map.of("id", content.reverseLedgerEntry(id, request.reason()));
    }

    private void requireAdmin(Jwt jwt) {
        if (jwt == null || !privileges.allows(
                jwt,
                PrivilegedCapability.SYSTEM_CONFIGURE
        )) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_PERMISSION_REQUIRED",
                    "Administrator permission required",
                    "Tài khoản không có quyền quản trị nội dung."
            );
        }
    }

    public record StoryRequest(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String teamId,
            @NotBlank @Pattern(regexp = UUID_PATTERN) String categoryId,
            @NotBlank @Pattern(regexp = SLUG_PATTERN) @Size(max = 160) String slug,
            @NotBlank @Size(max = 240) String title,
            @NotBlank @Size(max = 10_000) String synopsis,
            @NotBlank @Size(max = 160) String authorName,
            @NotBlank @Pattern(regexp = STATUS_PATTERN) String workflowStatus,
            @NotBlank @Pattern(regexp = STATUS_PATTERN) String completionStatus
    ) {
        StoryCommand command() {
            return new StoryCommand(
                    teamId,
                    categoryId,
                    slug,
                    title,
                    synopsis,
                    authorName,
                    workflowStatus,
                    completionStatus
            );
        }
    }

    public record CategoryRequest(
            @NotBlank @Pattern(regexp = SLUG_PATTERN) @Size(max = 80) String slug,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String description,
            @Min(0) @Max(10_000) int sortOrder,
            boolean active
    ) {
        CategoryCommand command() {
            return new CategoryCommand(
                    slug,
                    name,
                    description,
                    sortOrder,
                    active
            );
        }
    }

    public record TeamRequest(
            @NotBlank @Pattern(regexp = SLUG_PATTERN) @Size(max = 80) String slug,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 2_000) String description,
            @NotBlank @Pattern(regexp = UUID_PATTERN) String ownerUserId,
            @NotBlank @Pattern(regexp = STATUS_PATTERN) String state
    ) {
        TeamCommand command() {
            return new TeamCommand(
                    slug,
                    name,
                    description,
                    ownerUserId,
                    state
            );
        }
    }

    public record UserCreateRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 1_000) String bio,
            @NotBlank @Pattern(regexp = STATUS_PATTERN) String state,
            @NotEmpty @Size(max = 8) List<@Pattern(regexp = STATUS_PATTERN) String> roles
    ) {
        UserCommand command() {
            return new UserCommand(
                    email,
                    password,
                    displayName,
                    bio == null ? "" : bio,
                    state,
                    roles
            );
        }
    }

    public record UserUpdateRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 1_000) String bio,
            @NotBlank @Pattern(regexp = STATUS_PATTERN) String state,
            @NotEmpty @Size(max = 8) List<@Pattern(regexp = STATUS_PATTERN) String> roles
    ) {
        UserCommand command() {
            return new UserCommand(
                    email,
                    "not-used-on-update",
                    displayName,
                    bio == null ? "" : bio,
                    state,
                    roles
            );
        }
    }

    public record LedgerRequest(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String userId,
            @NotBlank @Pattern(regexp = STATUS_PATTERN) String entryType,
            long amountXu,
            @NotBlank @Pattern(regexp = STATUS_PATTERN) String referenceType,
            @NotBlank @Size(max = 100) String referenceId,
            @NotBlank @Size(max = 500) String description
    ) {
        LedgerCommand command() {
            if (amountXu == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "amountXu must not be zero"
                );
            }
            return new LedgerCommand(
                    userId,
                    entryType,
                    amountXu,
                    referenceType,
                    referenceId,
                    description
            );
        }
    }

    public record ReversalRequest(
            @NotBlank @Size(max = 300) String reason
    ) {
    }
}
