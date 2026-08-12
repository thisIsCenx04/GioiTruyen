package com.storyplatform.admin.api;

import com.storyplatform.author.application.AuthorApplicationService;
import com.storyplatform.author.application.AuthorApplicationService.ApplicationView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Deciding who may publish. Approving creates the applicant's team. */
@RestController
@RequestMapping("/admin/author-applications")
public class AdminAuthorApplicationController {

    private final AuthorApplicationService service;

    public AdminAuthorApplicationController(AuthorApplicationService service) {
        this.service = service;
    }

    public record ReviewRequest(String note) {
    }

    @GetMapping
    public List<ApplicationView> list(@RequestParam(required = false) String status) {
        return service.queue(status);
    }

    @PostMapping("/{applicationId}/approve")
    public ApplicationView approve(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ReviewRequest request
    ) {
        return service.approve(applicationId, reviewer(jwt), note(request));
    }

    @PostMapping("/{applicationId}/reject")
    public ApplicationView reject(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ReviewRequest request
    ) {
        return service.reject(applicationId, reviewer(jwt), note(request));
    }

    private static String note(ReviewRequest request) {
        return request == null ? null : request.note();
    }

    private static String reviewer(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
