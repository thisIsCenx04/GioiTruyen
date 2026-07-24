package com.storyplatform.publishing.api;

import com.storyplatform.publishing.application.PublishingScheduleException;
import com.storyplatform.publishing.application.PublishingScheduleOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class PublishingScheduleController {

    private final PublishingScheduleOperations schedules;

    public PublishingScheduleController(
            PublishingScheduleOperations schedules
    ) {
        this.schedules = Objects.requireNonNull(schedules, "schedules");
    }

    @PostMapping("/teams/{teamId}/stories/{storyId}/schedule")
    public ResponseEntity<PublishingScheduleOperations.ScheduleView> schedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId,
            @Valid @RequestBody ScheduleStoryRequest request
    ) {
        try {
            var schedule = schedules.schedule(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(storyId),
                    new PublishingScheduleOperations.ScheduleCommand(
                            request.publishAt(),
                            request.timeZone(),
                            uuid(request.revision())
                    )
            );
            return ResponseEntity.created(URI.create(
                            "/api/v1/teams/" + schedule.teamId()
                                    + "/stories/" + schedule.storyId()
                                    + "/schedule"
                    ))
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.ETAG, etag(schedule.version()))
                    .body(schedule);
        } catch (PublishingScheduleException exception) {
            throw problem(exception);
        }
    }

    @PatchMapping("/teams/{teamId}/stories/{storyId}/schedule")
    public ResponseEntity<PublishingScheduleOperations.ScheduleView> reschedule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody RescheduleStoryRequest request
    ) {
        try {
            var schedule = schedules.reschedule(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(storyId),
                    version(ifMatch),
                    new PublishingScheduleOperations.RescheduleCommand(
                            request.publishAt(),
                            request.timeZone()
                    )
            );
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.ETAG, etag(schedule.version()))
                    .body(schedule);
        } catch (PublishingScheduleException exception) {
            throw problem(exception);
        }
    }

    @DeleteMapping("/teams/{teamId}/stories/{storyId}/schedule")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
    ) {
        try {
            schedules.cancel(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(storyId),
                    version(ifMatch)
            );
            return ResponseEntity.noContent()
                    .cacheControl(CacheControl.noStore())
                    .build();
        } catch (PublishingScheduleException exception) {
            throw problem(exception);
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDENTIFIER_INVALID",
                    "Publishing schedule request rejected",
                    "A supplied identifier is invalid."
            );
        }
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[1-9][0-9]*\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Publishing schedule request rejected",
                    "If-Match must contain one quoted positive version."
            );
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Publishing schedule request rejected",
                    "If-Match version is outside the supported range."
            );
        }
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static ApiException problem(
            PublishingScheduleException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION -> HttpStatus.PRECONDITION_FAILED;
        };
        return new ApiException(
                status,
                exception.code(),
                "Publishing schedule request rejected",
                exception.getMessage()
        );
    }
}
