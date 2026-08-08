package com.storyplatform.reading.api;

import com.storyplatform.reading.application.ReadingHeartbeatOperations;
import com.storyplatform.reading.application.ReadingSessionException;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class ReadingHeartbeatController {

    private static final String SESSION_TOKEN_HEADER =
            "X-Reading-Session-Token";
    private final ReadingHeartbeatOperations heartbeats;

    public ReadingHeartbeatController(
            ReadingHeartbeatOperations heartbeats
    ) {
        this.heartbeats = Objects.requireNonNull(heartbeats);
    }

    @PostMapping("/reading-sessions/{sessionId}/heartbeats")
    public ResponseEntity<ReadingHeartbeatOperations.HeartbeatReceipt>
            ingest(
                    @PathVariable String sessionId,
                    @RequestHeader(SESSION_TOKEN_HEADER) String token,
                    @Valid @RequestBody ReadingHeartbeatRequest request
            ) {
        try {
            var receipt = heartbeats.ingest(
                    sessionId,
                    token,
                    new ReadingHeartbeatOperations.HeartbeatBatch(
                            request.batchId(),
                            request.heartbeats().stream()
                                    .map(item -> new ReadingHeartbeatOperations
                                            .Heartbeat(
                                            item.sequence(),
                                            item.occurredAt(),
                                            item.position(),
                                            item.activeSeconds()
                                    ))
                                    .toList()
                    )
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .cacheControl(CacheControl.noStore())
                    .body(receipt);
        } catch (ReadingSessionException exception) {
            HttpStatus status = exception.kind()
                    == ReadingSessionException.Kind.NOT_FOUND
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            throw new ApiException(
                    status,
                    exception.code(),
                    "Reading heartbeat rejected",
                    exception.getMessage()
            );
        }
    }
}
