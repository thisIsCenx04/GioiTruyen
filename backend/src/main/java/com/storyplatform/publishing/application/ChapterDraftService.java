package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port.ChapterDraftRepository;
import com.storyplatform.publishing.application.port.StoryDraftRepository;
import com.storyplatform.publishing.domain.ChapterDraft;
import com.storyplatform.publishing.domain.ChapterRevision;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ChapterDraftService implements ChapterDraftOperations {

    public static final String EDIT_PERMISSION = "story:edit";

    private final TeamPermissionAuthorizer permissions;
    private final TeamStatusDirectory teams;
    private final StoryDraftRepository stories;
    private final ChapterDraftRepository chapters;
    private final ChapterContentSanitizer sanitizer;
    private final Supplier<String> identifiers;
    private final Clock clock;

    public ChapterDraftService(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            StoryDraftRepository stories,
            ChapterDraftRepository chapters,
            ChapterContentSanitizer sanitizer,
            Supplier<String> identifiers,
            Clock clock
    ) {
        this.permissions = Objects.requireNonNull(
                permissions,
                "permissions"
        );
        this.teams = Objects.requireNonNull(teams, "teams");
        this.stories = Objects.requireNonNull(stories, "stories");
        this.chapters = Objects.requireNonNull(chapters, "chapters");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "identifiers"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ChapterView create(
            String actorId,
            String teamId,
            String storyId,
            CreateCommand command
    ) {
        if (!teams.isActive(teamId)
                || !permissions.allows(
                actorId,
                teamId,
                EDIT_PERMISSION
        )) {
            throw rejected(
                    "CHAPTER_CREATE_FORBIDDEN",
                    "An active Team membership with story:edit is required.",
                    ChapterDraftException.Kind.FORBIDDEN
            );
        }
        if (stories.findOwned(teamId, storyId).isEmpty()) {
            throw rejected(
                    "STORY_DRAFT_NOT_FOUND",
                    "The editable Team story does not exist.",
                    ChapterDraftException.Kind.NOT_FOUND
            );
        }
        if (command == null || command.number() < 1) {
            throw invalid("A positive chapter number is required.");
        }
        String title = normalizeTitle(command.title());
        if (chapters.numberExists(storyId, command.number())) {
            throw rejected(
                    "CHAPTER_NUMBER_EXISTS",
                    "The chapter number already exists in this story.",
                    ChapterDraftException.Kind.CONFLICT
            );
        }
        ChapterContentSanitizer.SanitizedContent content =
                sanitizer.sanitize(command.contentHtml());
        Instant now = clock.instant();
        String chapterId = uuid(identifiers.get(), "chapterId");
        String revisionId = uuid(identifiers.get(), "revisionId");
        ChapterDraft chapter = new ChapterDraft(
                chapterId,
                storyId,
                teamId,
                command.number(),
                slug(command.number(), title, chapterId),
                title,
                ChapterDraft.WorkflowStatus.DRAFT,
                revisionId,
                1,
                now,
                now,
                1
        );
        ChapterRevision revision = new ChapterRevision(
                revisionId,
                chapterId,
                1,
                content.html(),
                content.plainText(),
                checksum(content.html(), content.plainText()),
                actorId,
                now
        );
        chapters.insert(chapter, revision);
        return new ChapterView(
                chapter.id(),
                chapter.storyId(),
                chapter.teamId(),
                chapter.number(),
                chapter.slug(),
                chapter.title(),
                chapter.workflowStatus().name(),
                chapter.currentRevision(),
                chapter.currentRevisionNo(),
                chapter.version(),
                content.wordCount(),
                chapter.createdAt(),
                chapter.updatedAt()
        );
    }

    private static String normalizeTitle(String value) {
        if (value == null) {
            throw invalid("Chapter title is required.");
        }
        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFC
        ).trim();
        if (normalized.isBlank() || normalized.length() > 200) {
            throw invalid("Chapter title is invalid.");
        }
        return normalized;
    }

    private static String slug(
            int number,
            String title,
            String chapterId
    ) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "chapter";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60).replaceAll("-+$", "");
        }
        return "chapter-" + number + "-" + base + "-"
                + chapterId.replace("-", "").substring(0, 8);
    }

    private static String checksum(String html, String plainText) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            (html + "\u0000" + plainText).getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String uuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid(field + " must be a UUID.");
        }
    }

    private static ChapterDraftException invalid(String message) {
        return rejected(
                "CHAPTER_DRAFT_INVALID",
                message,
                ChapterDraftException.Kind.INVALID
        );
    }

    private static ChapterDraftException rejected(
            String code,
            String message,
            ChapterDraftException.Kind kind
    ) {
        return new ChapterDraftException(code, message, kind);
    }
}
