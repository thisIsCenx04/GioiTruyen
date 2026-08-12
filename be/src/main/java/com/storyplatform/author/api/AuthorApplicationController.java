package com.storyplatform.author.api;

import com.storyplatform.author.application.AuthorApplicationService;
import com.storyplatform.author.application.AuthorApplicationService.ApplicationView;
import com.storyplatform.author.application.AuthorApplicationService.ApplyRequest;
import com.storyplatform.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A reader asking for permission to publish. */
@RestController
@RequestMapping("/author-applications")
public class AuthorApplicationController {

    private final AuthorApplicationService service;

    public AuthorApplicationController(AuthorApplicationService service) {
        this.service = service;
    }

    /** Null when the reader has never applied. */
    @GetMapping("/me")
    public ApplicationView mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(currentUser(jwt));
    }

    @PostMapping
    public ApplicationView apply(@AuthenticationPrincipal Jwt jwt, @RequestBody ApplyRequest request) {
        return service.apply(currentUser(jwt), request);
    }

    private static UUID currentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để gửi yêu cầu.");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
