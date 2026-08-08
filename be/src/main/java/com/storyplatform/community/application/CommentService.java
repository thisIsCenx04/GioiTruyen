package com.storyplatform.community.application;

import com.storyplatform.community.application.port.CommentRepository;
import com.storyplatform.community.domain.Comment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CommentService implements CommentOperations {

    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(5);
    private final CommentRepository comments;
    private final CommentRateLimiter limiter;
    private final CommentSanitizer sanitizer;
    private final Clock clock;

    public CommentService(
            CommentRepository comments,
            CommentRateLimiter limiter,
            CommentSanitizer sanitizer,
            Clock clock
    ) {
        this.comments = Objects.requireNonNull(comments);
        this.limiter = Objects.requireNonNull(limiter);
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public CommentView create(String userId, CreateCommand command) {
        requireTarget(command.targetType(), command.targetId());
        if (!limiter.allow(userId)) {
            throw new CommentException(
                    "COMMENT_RATE_LIMITED",
                    "Too many comments. Try again later.",
                    CommentException.Kind.RATE_LIMITED,
                    limiter.retryAfterSeconds()
            );
        }
        String body = sanitizer.sanitize(command.body());
        String fingerprint = fingerprint(body);
        Instant now = clock.instant();
        if (comments.duplicateExists(
                userId,
                command.targetType(),
                command.targetId(),
                fingerprint,
                now.minus(DUPLICATE_WINDOW)
        )) {
            throw new CommentException(
                    "COMMENT_DUPLICATE",
                    "This comment was already submitted recently.",
                    CommentException.Kind.CONFLICT
            );
        }
        Parent parent = parent(command);
        String id = UUID.randomUUID().toString();
        Comment comment = new Comment(
                id,
                command.targetType(),
                command.targetId(),
                parent.id(),
                parent.rootId() == null ? id : parent.rootId(),
                parent.depth(),
                userId,
                body,
                Comment.Status.VISIBLE,
                1,
                now,
                now
        );
        comments.insert(comment, fingerprint);
        return view(comment);
    }

    @Override
    public CommentPage list(ListQuery query) {
        requireTarget(query.targetType(), query.targetId());
        if (query.limit() < 1 || query.limit() > 100) {
            throw invalid("Comment limit must be between 1 and 100.");
        }
        Cursor cursor = decode(query.cursor());
        List<Comment> found = comments.list(
                query.targetType(),
                query.targetId(),
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.id(),
                query.limit() + 1
        );
        boolean more = found.size() > query.limit();
        List<Comment> page = more
                ? found.subList(0, query.limit()) : found;
        String next = more ? encode(page.get(page.size() - 1)) : null;
        Set<String> authorIds = page.stream()
                .map(Comment::authorId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, CommentRepository.Author> authors =
                comments.authors(authorIds);
        return new CommentPage(
                page.stream()
                        .map(comment -> view(
                                comment,
                                authors.get(comment.authorId())
                        ))
                        .toList(),
                next,
                more
        );
    }

    @Override
    public CommentView update(
            String userId,
            String commentId,
            long expectedVersion,
            String rawBody
    ) {
        String body = sanitizer.sanitize(rawBody);
        boolean updated = comments.updateOwned(
                commentId,
                userId,
                expectedVersion,
                body,
                fingerprint(body),
                clock.instant()
        );
        if (!updated) {
            throw ownershipOrConflict(commentId, userId, expectedVersion);
        }
        return view(comments.find(commentId).orElseThrow());
    }

    @Override
    public CommentView delete(
            String userId,
            String commentId,
            long expectedVersion
    ) {
        boolean deleted = comments.deleteOwned(
                commentId,
                userId,
                expectedVersion,
                clock.instant()
        );
        if (!deleted) {
            throw ownershipOrConflict(commentId, userId, expectedVersion);
        }
        return view(comments.find(commentId).orElseThrow());
    }

    private Parent parent(CreateCommand command) {
        if (command.parentId() == null) {
            return new Parent(null, null, 0);
        }
        Comment parent = comments.find(command.parentId())
                .filter(value -> value.targetType() == command.targetType())
                .filter(value -> value.targetId().equals(command.targetId()))
                .filter(value -> value.status() != Comment.Status.DELETED)
                .orElseThrow(() -> invalid("Parent comment is invalid."));
        if (parent.depth() >= Comment.MAXIMUM_DEPTH) {
            throw invalid("Comment thread cannot exceed 3 levels.");
        }
        return new Parent(parent.id(), parent.rootId(), parent.depth() + 1);
    }

    private RuntimeException ownershipOrConflict(
            String commentId,
            String userId,
            long version
    ) {
        return comments.find(commentId)
                .filter(comment -> comment.authorId().equals(userId))
                .filter(comment -> comment.status() != Comment.Status.DELETED)
                .map(comment -> new CommentException(
                        "COMMENT_VERSION_CONFLICT",
                        "Comment changed. Reload before trying again.",
                        CommentException.Kind.CONFLICT
                ))
                .orElseGet(() -> new CommentException(
                        "COMMENT_NOT_FOUND",
                        "Comment does not exist.",
                        CommentException.Kind.NOT_FOUND
                ));
    }

    private void requireTarget(Comment.TargetType type, String targetId) {
        if (type == null || !comments.targetIsPublished(type, targetId)) {
            throw new CommentException(
                    "COMMENT_TARGET_NOT_FOUND",
                    "Comment target does not exist.",
                    CommentException.Kind.NOT_FOUND
            );
        }
    }

    private CommentView view(Comment comment) {
        return view(comment, comments.author(comment.authorId()));
    }

    private static CommentView view(
            Comment comment,
            CommentRepository.Author author
    ) {
        return new CommentView(
                comment.id(),
                comment.targetType(),
                comment.targetId(),
                comment.parentId(),
                comment.rootId(),
                comment.depth(),
                new AuthorView(
                        author.id(),
                        author.displayName(),
                        author.avatarMediaId()
                ),
                comment.status() == Comment.Status.DELETED
                        ? "Bình luận đã được xóa." : comment.body(),
                comment.status(),
                comment.version(),
                comment.createdAt(),
                comment.updatedAt()
        );
    }

    private static String fingerprint(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    body.toLowerCase(java.util.Locale.ROOT)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encode(Comment comment) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (comment.createdAt() + "|" + comment.id())
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8
            );
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            UUID.fromString(parts[1]);
            return new Cursor(Instant.parse(parts[0]), parts[1]);
        } catch (RuntimeException exception) {
            throw invalid("Comment cursor is invalid.");
        }
    }

    private static CommentException invalid(String message) {
        return new CommentException(
                "COMMENT_INVALID",
                message,
                CommentException.Kind.INVALID
        );
    }

    private record Parent(String id, String rootId, int depth) {
    }

    private record Cursor(Instant createdAt, String id) {
    }
}
