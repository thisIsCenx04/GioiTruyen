package com.storyplatform.publishing.application;

import com.storyplatform.catalog.application.contract.ActiveCategoryDirectory;
import com.storyplatform.publishing.application.port.StoryDraftRepository;
import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.domain.StoryRevision;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class StoryDraftService implements StoryDraftOperations {

    public static final String CREATE_PERMISSION = "story:create";
    public static final String EVENT_TYPE =
            "publishing.story.draftcreated";

    private final TeamPermissionAuthorizer permissions;
    private final TeamStatusDirectory teams;
    private final ActiveCategoryDirectory categories;
    private final StoryDraftRepository drafts;
    private final OutboxAppender outbox;
    private final Supplier<String> identifiers;
    private final Clock clock;

    public StoryDraftService(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            ActiveCategoryDirectory categories,
            StoryDraftRepository drafts,
            OutboxAppender outbox,
            Supplier<String> identifiers,
            Clock clock
    ) {
        this.permissions = Objects.requireNonNull(
                permissions,
                "permissions"
        );
        this.teams = Objects.requireNonNull(teams, "teams");
        this.categories = Objects.requireNonNull(categories, "categories");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DraftView create(
            String actorId,
            String teamId,
            String idempotencyKey,
            CreateCommand command
    ) {
        String key = requireIdempotencyKey(idempotencyKey);
        if (!teams.isActive(teamId)
                || !permissions.allows(
                actorId,
                teamId,
                CREATE_PERMISSION
        )) {
            throw rejected(
                    "STORY_CREATE_FORBIDDEN",
                    "An active Team membership with story:create is required.",
                    StoryDraftException.Kind.FORBIDDEN
            );
        }
        Normalized normalized = normalize(command);
        String fingerprint = fingerprint(normalized);
        var replay = drafts.findReplay(teamId, key);
        if (replay.isPresent()) {
            var stored = replay.orElseThrow();
            if (!stored.idempotencyFingerprint().equals(fingerprint)) {
                throw rejected(
                        "IDEMPOTENCY_KEY_REUSED",
                        "The idempotency key belongs to another request.",
                        StoryDraftException.Kind.CONFLICT
                );
            }
            return view(stored.story(), stored.revisionNo());
        }

        Instant now = clock.instant();
        String storyId = uuid(identifiers.get(), "storyId");
        String revisionId = uuid(identifiers.get(), "revisionId");
        StoryDraft story = new StoryDraft(
                storyId,
                teamId,
                slug(normalized.title(), storyId),
                normalized.title(),
                normalized.synopsis(),
                normalized.categoryIds(),
                normalized.origin(),
                normalized.language(),
                StoryDraft.CompletionStatus.ONGOING,
                StoryDraft.WorkflowStatus.DRAFT,
                revisionId,
                normalized.coverAssetId(),
                now,
                now,
                1
        );
        StoryRevision.Snapshot snapshot = new StoryRevision.Snapshot(
                story.title(),
                story.synopsis(),
                story.origin(),
                story.language(),
                story.categoryIds(),
                story.coverAssetId()
        );
        StoryRevision revision = new StoryRevision(
                revisionId,
                storyId,
                1,
                snapshot,
                actorId,
                checksum(snapshot),
                now
        );
        drafts.insert(
                story,
                revision,
                actorId,
                key,
                fingerprint
        );
        outbox.append(new IntegrationEvent(
                UUID.fromString(uuid(identifiers.get(), "eventId")),
                EVENT_TYPE,
                1,
                now,
                key,
                "story",
                storyId,
                actorId,
                teamId,
                new StoryDraftCreated(
                        teamId,
                        storyId,
                        revisionId,
                        1
                )
        ));
        return view(story, 1);
    }

    private Normalized normalize(CreateCommand command) {
        if (command == null) {
            throw invalid("Story draft body is required.");
        }
        String title = normalizedText(command.title(), 200, "title");
        String synopsis = normalizedText(
                command.synopsis(),
                5000,
                "synopsis"
        );
        String language = normalizedText(
                command.language(),
                16,
                "language"
        );
        if (!language.matches("[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) {
            throw invalid("language is invalid.");
        }
        if (command.origin() == null
                || command.categoryIds() == null
                || command.categoryIds().isEmpty()
                || command.categoryIds().size() > 30) {
            throw invalid("Story origin and taxonomy are required.");
        }
        List<String> requested = command.categoryIds().stream()
                .map(value -> uuid(value, "categoryId"))
                .distinct()
                .sorted()
                .toList();
        if (requested.size() != command.categoryIds().size()) {
            throw invalid("Story taxonomy contains duplicates.");
        }
        var active = categories.activeCategoryIds();
        if (!active.containsAll(requested)) {
            throw invalid("Story taxonomy contains an inactive category.");
        }
        String cover = command.coverAssetId() == null
                ? null
                : uuid(command.coverAssetId(), "coverAssetId");
        return new Normalized(
                title,
                synopsis,
                command.origin(),
                language,
                requested,
                cover
        );
    }

    private static String slug(String title, String storyId) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "story";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).replaceAll("-+$", "");
        }
        return base + "-" + storyId.replace("-", "").substring(0, 8);
    }

    private static String fingerprint(Normalized value) {
        return digest(canonical(value));
    }

    private static String checksum(StoryRevision.Snapshot value) {
        return digest(canonical(new Normalized(
                value.title(),
                value.synopsis(),
                value.origin(),
                value.language(),
                value.categoryIds(),
                value.coverAssetId()
        )));
    }

    private static String canonical(Normalized value) {
        String categories = value.categoryIds().stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));
        return String.join(
                "\u0000",
                value.title(),
                value.synopsis(),
                value.origin().name(),
                value.language(),
                categories,
                value.coverAssetId() == null
                        ? ""
                        : value.coverAssetId()
        );
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static DraftView view(StoryDraft story, long revisionNo) {
        return new DraftView(
                story.id(),
                story.teamId(),
                story.slug(),
                story.title(),
                story.synopsis(),
                story.origin(),
                story.language(),
                story.categoryIds(),
                story.coverAssetId(),
                story.completionStatus(),
                story.workflowStatus(),
                story.currentRevision(),
                revisionNo,
                story.version(),
                story.createdAt(),
                story.updatedAt()
        );
    }

    private static String normalizedText(
            String value,
            int maximum,
            String field
    ) {
        if (value == null) {
            throw invalid(field + " is required.");
        }
        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFC
        ).trim();
        if (normalized.isBlank() || normalized.length() > maximum) {
            throw invalid(field + " is invalid.");
        }
        return normalized;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")) {
            throw invalid("A valid Idempotency-Key header is required.");
        }
        return value;
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(field + " must be a UUID.");
        }
    }

    private static StoryDraftException invalid(String message) {
        return rejected(
                "STORY_DRAFT_INVALID",
                message,
                StoryDraftException.Kind.INVALID
        );
    }

    private static StoryDraftException rejected(
            String code,
            String message,
            StoryDraftException.Kind kind
    ) {
        return new StoryDraftException(code, message, kind);
    }

    public record StoryDraftCreated(
            String teamId,
            String storyId,
            String revisionId,
            long revisionNo
    ) {
    }

    private record Normalized(
            String title,
            String synopsis,
            StoryDraft.Origin origin,
            String language,
            List<String> categoryIds,
            String coverAssetId
    ) {
    }
}
