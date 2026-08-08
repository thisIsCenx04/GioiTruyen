package com.storyplatform.unit.publishing.api;

import com.storyplatform.publishing.api.PublishingScheduleController;
import com.storyplatform.publishing.api.RescheduleStoryRequest;
import com.storyplatform.publishing.api.ScheduleStoryRequest;
import com.storyplatform.publishing.application.PublishingScheduleException;
import com.storyplatform.publishing.application.PublishingScheduleOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingScheduleControllerTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "30000000-0000-4000-8000-000000000001";
    private static final String REVISION =
            "40000000-0000-4000-8000-000000000001";
    private PublishingScheduleOperations operations;
    private PublishingScheduleController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        operations = mock(PublishingScheduleOperations.class);
        controller = new PublishingScheduleController(operations);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(ACTOR)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    @Test
    void createsReschedulesAndCancelsWithSafeHeaders() {
        when(operations.schedule(any(), any(), any(), any()))
                .thenReturn(view(1));
        var created = controller.schedule(
                jwt, TEAM, STORY, scheduleRequest()
        );
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(created.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(created.getHeaders().getLocation().toString())
                .endsWith("/stories/" + STORY + "/schedule");

        when(operations.reschedule(
                any(), any(), any(), any(Long.class), any()
        )).thenReturn(view(2));
        var changed = controller.reschedule(
                jwt,
                TEAM,
                STORY,
                "\"1\"",
                new RescheduleStoryRequest(
                        OffsetDateTime.parse(
                                "2026-07-26T07:00:00+07:00"
                        ),
                        "Asia/Ho_Chi_Minh"
                )
        );
        assertThat(changed.getHeaders().getETag()).isEqualTo("\"2\"");
        assertThat(controller.cancel(
                jwt, TEAM, STORY, "\"2\""
        ).getStatusCode().value()).isEqualTo(204);
        verify(operations).cancel(ACTOR, TEAM, STORY, 2);
    }

    @Test
    void mapsEveryFailureAndRejectsMalformedInputs() {
        for (PublishingScheduleException.Kind kind
                : PublishingScheduleException.Kind.values()) {
            int expected = switch (kind) {
                case INVALID -> 400;
                case FORBIDDEN -> 403;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case PRECONDITION -> 412;
            };
            org.mockito.Mockito.reset(operations);
            when(operations.schedule(any(), any(), any(), any()))
                    .thenThrow(new PublishingScheduleException(
                            "SCHEDULE_REJECTED",
                            "rejected",
                            kind
                    ));
            assertThatThrownBy(() -> controller.schedule(
                    jwt, TEAM, STORY, scheduleRequest()
            )).isInstanceOfSatisfying(ApiException.class, exception ->
                    assertThat(exception.status().value())
                            .isEqualTo(expected)
            );
        }
        assertBad(() -> controller.schedule(
                jwt, "invalid", STORY, scheduleRequest()
        ));
        assertBad(() -> controller.reschedule(
                jwt,
                TEAM,
                STORY,
                "1",
                new RescheduleStoryRequest(
                        OffsetDateTime.parse(
                                "2026-07-26T07:00:00+07:00"
                        ),
                        "Asia/Ho_Chi_Minh"
                )
        ));
        assertBad(() -> controller.cancel(
                jwt, TEAM, STORY, "\"" + "9".repeat(30) + "\""
        ));
    }

    private static ScheduleStoryRequest scheduleRequest() {
        return new ScheduleStoryRequest(
                OffsetDateTime.parse("2026-07-25T07:00:00+07:00"),
                "Asia/Ho_Chi_Minh",
                REVISION
        );
    }

    private static PublishingScheduleOperations.ScheduleView view(
            long version
    ) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new PublishingScheduleOperations.ScheduleView(
                "50000000-0000-4000-8000-000000000001",
                STORY,
                TEAM,
                REVISION,
                1,
                "SCHEDULED",
                now.plusSeconds(3600),
                "Asia/Ho_Chi_Minh",
                version,
                now,
                now
        );
    }

    private static void assertBad(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.status().value()).isEqualTo(400)
                );
    }
}
