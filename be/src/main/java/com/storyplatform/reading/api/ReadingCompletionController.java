package com.storyplatform.reading.api;

import com.storyplatform.reading.application.ReadingCompletionOperations;
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
public final class ReadingCompletionController {

    private final ReadingCompletionOperations completions;

    public ReadingCompletionController(
            ReadingCompletionOperations completions
    ) {
        this.completions = Objects.requireNonNull(completions);
    }

    @PostMapping("/reading-sessions/{sessionId}/complete")
    public ResponseEntity<ReadingCompletionOperations.CompletionReceipt>
            complete(
                    @PathVariable String sessionId,
                    @RequestHeader("X-Reading-Session-Token") String token,
                    @Valid @RequestBody ReadingCompletionRequest request
            ) {
        try {
            var receipt = completions.complete(
                    sessionId,
                    token,
                    new ReadingCompletionOperations.CompletionCommand(
                            request.completionId(),
                            request.finalSequence(),
                            request.occurredAt(),
                            request.position()
                    )
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .cacheControl(CacheControl.noStore())
                    .body(receipt);
        } catch (ReadingSessionException exception) {
            throw new ApiException(
                    exception.kind()
                            == ReadingSessionException.Kind.NOT_FOUND
                            ? HttpStatus.NOT_FOUND
                            : HttpStatus.BAD_REQUEST,
                    exception.code(),
                    "Reading completion rejected",
                    exception.getMessage()
            );
        }
    }
}
