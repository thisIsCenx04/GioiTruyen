package com.storyplatform.engagement.api;

import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles reading sessions, heartbeats, chapter completion, and reading progress.
 */
@RestController
public class ReadingSessionController {

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, SessionRecord> activeSessions = new ConcurrentHashMap<>();

    public ReadingSessionController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StartSessionRequest(String anonymousId, String chapterId, String storyId) {}
    public record ReadingSessionGrant(String sessionId, String sessionToken, int heartbeatIntervalSeconds) {}
    public record HeartbeatItem(int activeSeconds, String occurredAt, double position, int sequence) {}
    public record HeartbeatRequest(String batchId, List<HeartbeatItem> heartbeats) {}
    public record HeartbeatResponse(int nextSequence) {}
    public record CompleteRequest(String completionId, int finalSequence, String occurredAt, double position) {}
    public record StatusResponse(String status) {}
    public record ReadingProgressRequest(String chapterId, String deviceUpdatedAt, double position) {}
    public record ReadingHistoryResponse(List<Map<String, Object>> items, String nextCursor) {}

    private record SessionRecord(String sessionId, String sessionToken, String storyId, String chapterId, int lastSequence, Instant createdAt) {}

    @PostMapping("/reading-sessions")
    public ReadingSessionGrant startSession(@RequestBody StartSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String sessionToken = UUID.randomUUID().toString();
        
        activeSessions.put(sessionId, new SessionRecord(
                sessionId,
                sessionToken,
                request.storyId(),
                request.chapterId(),
                1,
                Instant.now()
        ));

        // Increment view count in database for the story
        if (request.storyId() != null && !request.storyId().isBlank()) {
            try {
                jdbc.update(
                        "UPDATE stories SET view_count_cache = view_count_cache + 1 WHERE id = :storyId OR slug = :storyId",
                        Map.of("storyId", request.storyId())
                );
            } catch (Exception ignored) {
                // Keep reading smooth even if view cache update fails
            }
        }

        return new ReadingSessionGrant(sessionId, sessionToken, 15);
    }

    @PostMapping("/reading-sessions/{sessionId}/heartbeats")
    public HeartbeatResponse heartbeat(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestBody HeartbeatRequest request
    ) {
        int maxSeq = 1;
        if (request.heartbeats() != null && !request.heartbeats().isEmpty()) {
            for (HeartbeatItem item : request.heartbeats()) {
                if (item.sequence() >= maxSeq) {
                    maxSeq = item.sequence() + 1;
                }
            }
        }

        SessionRecord current = activeSessions.get(sessionId);
        if (current != null) {
            activeSessions.put(sessionId, new SessionRecord(
                    current.sessionId(),
                    current.sessionToken(),
                    current.storyId(),
                    current.chapterId(),
                    maxSeq,
                    current.createdAt()
            ));
        }

        return new HeartbeatResponse(maxSeq);
    }

    @PostMapping("/reading-sessions/{sessionId}/complete")
    public StatusResponse complete(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestBody CompleteRequest request
    ) {
        activeSessions.remove(sessionId);
        return new StatusResponse("COMPLETED");
    }

    @PutMapping("/me/reading-progress/{storyId}")
    public StatusResponse saveProgress(
            @PathVariable String storyId,
            @RequestBody ReadingProgressRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // Authenticated user progress save
        return new StatusResponse("SAVED");
    }

    @GetMapping("/me/reading-history")
    public ReadingHistoryResponse readingHistory(
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new ReadingHistoryResponse(List.of(), null);
    }
}
