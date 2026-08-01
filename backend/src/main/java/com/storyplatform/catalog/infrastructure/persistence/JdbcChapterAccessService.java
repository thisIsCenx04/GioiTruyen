package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.ChapterAccessOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
public class JdbcChapterAccessService implements ChapterAccessOperations {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcChapterAccessService(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Clock.systemUTC();
    }

    @Override
    public ChapterAccessView status(String chapterId, String userId) {
        ChapterPrice chapter = loadChapter(chapterId);
        boolean authenticated = userId != null && !userId.isBlank();
        boolean unlocked = chapter.priceXu() == 0
                || authenticated && isUnlocked(chapterId, userId);
        long availableXu = authenticated ? walletBalance(userId) : 0;
        return view(chapter, authenticated, unlocked, availableXu);
    }

    @Override
    @Transactional
    public ChapterAccessView unlock(String chapterId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw problem(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "Vui lòng đăng nhập để mở khóa chương."
            );
        }
        ChapterPrice chapter = loadChapter(chapterId);
        if (chapter.priceXu() == 0 || isUnlocked(chapterId, userId)) {
            return status(chapterId, userId);
        }
        long balance = jdbc.sql("""
                        SELECT available_xu
                        FROM wallets
                        WHERE user_id = :userId
                        FOR UPDATE
                        """)
                .param("userId", userId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (balance < chapter.priceXu()) {
            throw problem(
                    HttpStatus.CONFLICT,
                    "CHAPTER_UNLOCK_INSUFFICIENT_BALANCE",
                    "Số dư XU không đủ để mở khóa chương này."
            );
        }
        String ledgerId = UUID.randomUUID().toString();
        jdbc.sql("""
                UPDATE wallets
                SET available_xu = available_xu - :priceXu,
                    updated_at = :now,
                    version = version + 1
                WHERE user_id = :userId
                """)
                .param("priceXu", chapter.priceXu())
                .param("now", clock.instant())
                .param("userId", userId)
                .update();
        jdbc.sql("""
                INSERT INTO ledger_entries (
                    id, user_id, entry_type, amount_xu, reference_type,
                    reference_id, description, created_at
                ) VALUES (
                    :id, :userId, 'CHAPTER_UNLOCK', :amountXu, 'CHAPTER',
                    :chapterId, :description, :now
                )
                """)
                .param("id", ledgerId)
                .param("userId", userId)
                .param("amountXu", -chapter.priceXu())
                .param("chapterId", chapterId)
                .param("description", "Mở khóa chương: " + chapter.title())
                .param("now", clock.instant())
                .update();
        jdbc.sql("""
                INSERT INTO chapter_unlocks (
                    user_id, chapter_id, price_xu,
                    ledger_entry_id, unlocked_at
                ) VALUES (
                    :userId, :chapterId, :priceXu, :ledgerId, :now
                )
                """)
                .param("userId", userId)
                .param("chapterId", chapterId)
                .param("priceXu", chapter.priceXu())
                .param("ledgerId", ledgerId)
                .param("now", clock.instant())
                .update();
        return view(
                chapter,
                true,
                true,
                balance - chapter.priceXu()
        );
    }

    @Override
    public void requireReadable(String chapterId, String userId) {
        ChapterAccessView access = status(chapterId, userId);
        if (!access.unlocked()) {
            throw problem(
                    HttpStatus.PAYMENT_REQUIRED,
                    "CHAPTER_LOCKED",
                    "Chương này cần được mở khóa bằng XU trước khi đọc."
            );
        }
    }

    private ChapterPrice loadChapter(String chapterId) {
        return jdbc.sql("""
                        SELECT c.id, c.story_id, c.title,
                               COALESCE(p.price_xu, 0) AS price_xu
                        FROM chapters c
                        LEFT JOIN chapter_prices p ON p.chapter_id = c.id
                        WHERE c.id = :chapterId
                          AND c.workflow_status = 'PUBLISHED'
                        """)
                .param("chapterId", chapterId)
                .query((result, rowNumber) -> new ChapterPrice(
                        result.getString("id"),
                        result.getString("story_id"),
                        result.getString("title"),
                        result.getLong("price_xu")
                ))
                .optional()
                .orElseThrow(() -> problem(
                        HttpStatus.NOT_FOUND,
                        "CHAPTER_NOT_FOUND",
                        "Không tìm thấy chương truyện."
                ));
    }

    private boolean isUnlocked(String chapterId, String userId) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM chapter_unlocks
                        WHERE chapter_id = :chapterId
                          AND user_id = :userId
                        """)
                .param("chapterId", chapterId)
                .param("userId", userId)
                .query(Long.class)
                .single() > 0;
    }

    private long walletBalance(String userId) {
        return jdbc.sql("""
                        SELECT available_xu
                        FROM wallets
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    private static ChapterAccessView view(
            ChapterPrice chapter,
            boolean authenticated,
            boolean unlocked,
            long availableXu
    ) {
        return new ChapterAccessView(
                chapter.id(),
                chapter.storyId(),
                chapter.title(),
                chapter.priceXu(),
                unlocked,
                authenticated,
                availableXu
        );
    }

    private static ApiException problem(
            HttpStatus status,
            String code,
            String detail
    ) {
        return new ApiException(
                status,
                code,
                "Chapter access rejected",
                detail
        );
    }

    private record ChapterPrice(
            String id,
            String storyId,
            String title,
            long priceXu
    ) {
    }
}
