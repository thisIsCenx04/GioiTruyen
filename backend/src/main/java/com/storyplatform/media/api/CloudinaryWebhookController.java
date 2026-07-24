package com.storyplatform.media.api;

import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.MediaWebhookOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@ConditionalOnProperty(
        name = "app.media.cloudinary.webhook-enabled",
        havingValue = "true"
)
public final class CloudinaryWebhookController {

    private final MediaWebhookOperations webhooks;

    public CloudinaryWebhookController(MediaWebhookOperations webhooks) {
        this.webhooks = Objects.requireNonNull(webhooks, "webhooks");
    }

    @PostMapping("/webhooks/cloudinary")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] body,
            @RequestHeader("X-Cld-Timestamp") String timestamp,
            @RequestHeader("X-Cld-Signature") String signature
    ) {
        try {
            webhooks.accept(body, timestamp, signature);
            return ResponseEntity.noContent().build();
        } catch (MediaRequestException exception) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    exception.code(),
                    "Cloudinary webhook rejected",
                    exception.getMessage()
            );
        }
    }
}
