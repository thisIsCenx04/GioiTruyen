package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port
        .ContentVisibilityRepository;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ContentVisibilityService
        implements ContentVisibilityOperations {

    public static final String PUBLISH_PERMISSION = "story:publish";
    public static final String EVENT_TYPE =
            "publishing.visibility.changed";

    private final TeamPermissionAuthorizer permissions;
    private final TeamStatusDirectory teams;
    private final ContentVisibilityRepository repository;
    private final OutboxAppender outbox;
    private final Supplier<String> identifiers;
    private final Clock clock;

    public ContentVisibilityService(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            ContentVisibilityRepository repository,
            OutboxAppender outbox,
            Supplier<String> identifiers,
            Clock clock
    ) {
        this.permissions = Objects.requireNonNull(permissions);
        this.teams = Objects.requireNonNull(teams);
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.identifiers = Objects.requireNonNull(identifiers);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public VisibilityView teamChange(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion,
            VisibilityCommand command
    ) {
        if (!teams.isActive(teamId)
                || !permissions.allows(
                actorId, teamId, PUBLISH_PERMISSION
        )) {
            throw error(
                    "CONTENT_VISIBILITY_FORBIDDEN",
                    "An active Team membership with story:publish is required.",
                    ContentVisibilityException.Kind.FORBIDDEN
            );
        }
        var candidate = candidate(storyId);
        if (!candidate.teamId().equals(teamId)) {
            throw error(
                    "CONTENT_NOT_FOUND",
                    "The Team story does not exist.",
                    ContentVisibilityException.Kind.NOT_FOUND
            );
        }
        Action action = command(command);
        if (action == Action.SUSPEND) {
            throw invalid("Team actors cannot suspend content.");
        }
        String target = switch (action) {
            case HIDE -> require(candidate.state(), "PUBLISHED", "HIDDEN");
            case REINSTATE -> require(
                    candidate.state(), "HIDDEN", "PUBLISHED"
            );
            default -> throw invalid("Unsupported Team action.");
        };
        return change(
                candidate, expectedVersion, command, target, actorId
        );
    }

    @Override
    public VisibilityView moderationChange(
            String actorId,
            String storyId,
            long expectedVersion,
            VisibilityCommand command
    ) {
        var candidate = candidate(storyId);
        Action action = command(command);
        String target = switch (action) {
            case SUSPEND -> {
                if (!candidate.state().equals("PUBLISHED")
                        && !candidate.state().equals("HIDDEN")) {
                    throw conflict();
                }
                yield "SUSPENDED";
            }
            case REINSTATE -> require(
                    candidate.state(),
                    "SUSPENDED",
                    candidate.previousState() == null
                            ? "PUBLISHED"
                            : candidate.previousState()
            );
            default -> throw invalid("Moderator action is invalid.");
        };
        return change(
                candidate, expectedVersion, command, target, actorId
        );
    }

    private VisibilityView change(
            ContentVisibilityRepository.Candidate candidate,
            long expectedVersion,
            VisibilityCommand command,
            String target,
            String actorId
    ) {
        if (expectedVersion < 1
                || expectedVersion != candidate.version()) {
            throw error(
                    "CONTENT_VISIBILITY_STALE",
                    "The content version is stale.",
                    ContentVisibilityException.Kind.PRECONDITION
            );
        }
        String reason = reason(command.reasonCode());
        String note = note(command.note());
        Instant now = clock.instant();
        if (!repository.change(
                candidate,
                target,
                actorId,
                command.action().name(),
                reason,
                note,
                now
        )) {
            throw error(
                    "CONTENT_VISIBILITY_RACE",
                    "Another visibility transition won.",
                    ContentVisibilityException.Kind.PRECONDITION
            );
        }
        outbox.append(new IntegrationEvent(
                UUID.fromString(identifiers.get()),
                EVENT_TYPE,
                1,
                now,
                candidate.storyId(),
                "story",
                candidate.storyId(),
                actorId,
                candidate.teamId(),
                Map.of(
                        "storyId", candidate.storyId(),
                        "teamId", candidate.teamId(),
                        "state", target,
                        "reasonCode", reason,
                        "version", expectedVersion + 1
                )
        ));
        return new VisibilityView(
                candidate.storyId(),
                candidate.teamId(),
                target,
                expectedVersion + 1,
                now
        );
    }

    private ContentVisibilityRepository.Candidate candidate(
            String storyId
    ) {
        try {
            storyId = UUID.fromString(storyId).toString();
        } catch (RuntimeException exception) {
            throw invalid("storyId must be a UUID.");
        }
        String id = storyId;
        return repository.find(id).orElseThrow(() -> error(
                "CONTENT_NOT_FOUND",
                "The published story does not exist.",
                ContentVisibilityException.Kind.NOT_FOUND
        ));
    }

    private static Action command(VisibilityCommand value) {
        if (value == null || value.action() == null) {
            throw invalid("A visibility action is required.");
        }
        return value.action();
    }

    private static String reason(String value) {
        if (value == null
                || !value.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw invalid("reasonCode has an invalid format.");
        }
        return value;
    }

    private static String note(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() > 1000) {
            throw invalid("note exceeds 1000 characters.");
        }
        return normalized;
    }

    private static String require(
            String current,
            String required,
            String target
    ) {
        if (!current.equals(required)) {
            throw conflict();
        }
        return target;
    }

    private static ContentVisibilityException conflict() {
        return error(
                "CONTENT_VISIBILITY_CONFLICT",
                "The requested state transition is not allowed.",
                ContentVisibilityException.Kind.CONFLICT
        );
    }

    private static ContentVisibilityException invalid(String message) {
        return error(
                "CONTENT_VISIBILITY_INVALID",
                message,
                ContentVisibilityException.Kind.INVALID
        );
    }

    private static ContentVisibilityException error(
            String code,
            String message,
            ContentVisibilityException.Kind kind
    ) {
        return new ContentVisibilityException(code, message, kind);
    }
}
