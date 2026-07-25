package com.storyplatform.bootstrap.persistence.seed;

import com.storyplatform.identity.application.port.PasswordHasher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(
        name = "app.seed.enabled",
        havingValue = "true"
)
public class DevelopmentDataSeeder implements ApplicationRunner {

    private static final Instant NOW =
            Instant.parse("2026-07-26T00:00:00Z");

    private final JdbcClient jdbc;
    private final PasswordHasher passwords;

    public DevelopmentDataSeeder(
            JdbcClient jdbc,
            PasswordHasher passwords
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        seedUser(
                "10000000-0000-0000-0000-000000000001",
                "reader@gioitruyen.local",
                "reader123456",
                "Độc giả Mộc Miên",
                "Đọc chậm, ghi chú kỹ và luôn chờ chương mới.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000002",
                "author@gioitruyen.local",
                "author123456",
                "Tác giả Lam Dạ",
                "Tác giả của những câu chuyện kỳ ảo Việt Nam.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000003",
                "moderator@gioitruyen.local",
                "moderator123456",
                "Kiểm duyệt viên An",
                "Phụ trách hàng chờ xuất bản và báo cáo nội dung.",
                List.of("USER", "MODERATOR")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000004",
                "admin@gioitruyen.local",
                "dtpo9094",
                "Quản trị Giới Truyện",
                "Quản trị hệ thống.",
                List.of("USER", "ADMIN")
        );

        seedTeamAndMembership();
        seedCategories();
        seedStoriesAndChapters();
        seedExpandedLibrary();
        seedReaderExperience();
        seedModerationAndWallet();
        seedFunctionalScenarios();
    }

    private void seedUser(
            String id,
            String email,
            String rawPassword,
            String displayName,
            String bio,
            List<String> roles
    ) {
        jdbc.sql("""
                INSERT INTO users (
                    id, email_normalized, password_hash, state,
                    security_version, accepted_consent_version,
                    consent_accepted_at, created_at, updated_at, version
                ) VALUES (
                    :id, :email, :passwordHash, 'ACTIVE',
                    1, '2026-07-24', :now, :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", id)
                .param("email", email)
                .param("passwordHash", passwords.hash(rawPassword))
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO user_profiles (
                    user_id, display_name, bio, created_at, updated_at, version
                ) VALUES (:id, :displayName, :bio, :now, :now, 0)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("id", id)
                .param("displayName", displayName)
                .param("bio", bio)
                .param("now", NOW)
                .update();
        roles.forEach(role -> jdbc.sql("""
                        INSERT INTO user_roles (user_id, role)
                        VALUES (:id, :role)
                        ON DUPLICATE KEY UPDATE user_id = user_id
                        """)
                .param("id", id)
                .param("role", role)
                .update());
    }

    private void seedTeamAndMembership() {
        jdbc.sql("""
                INSERT INTO teams (
                    id, slug, name, description, owner_user_id, state,
                    created_at, updated_at, version
                ) VALUES (
                    '20000000-0000-0000-0000-000000000001',
                    'lam-da', 'Lam Dạ Các',
                    'Nhóm sáng tác kỳ ảo lấy cảm hứng từ văn hóa Việt.',
                    '10000000-0000-0000-0000-000000000002',
                    'ACTIVE', :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO team_memberships (
                    team_id, user_id, role, permissions, state,
                    joined_at, updated_at, version
                ) VALUES (
                    '20000000-0000-0000-0000-000000000001',
                    '10000000-0000-0000-0000-000000000002',
                    'OWNER',
                    JSON_ARRAY('STORY_CREATE', 'STORY_EDIT', 'MEMBER_MANAGE'),
                    'ACTIVE', :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE team_id = team_id
                """)
                .param("now", NOW)
                .update();
        seedMembership(
                "10000000-0000-0000-0000-000000000003",
                "MODERATOR",
                "STORY_REVIEW"
        );
        seedMembership(
                "10000000-0000-0000-0000-000000000004",
                "MANAGER",
                "FINANCE_REVIEW"
        );
        jdbc.sql("""
                INSERT INTO team_invitations (
                    id, team_id, target_user_id, invited_by,
                    permissions, token_hash, idempotency_key, state,
                    expires_at, created_at, version
                ) VALUES (
                    '21000000-0000-0000-0000-000000000001',
                    '20000000-0000-0000-0000-000000000001',
                    '10000000-0000-0000-0000-000000000001',
                    '10000000-0000-0000-0000-000000000002',
                    JSON_ARRAY('STORY_COMMENT'),
                    'seed-team-invitation-token',
                    'seed-team-invitation', 'PENDING',
                    :expiresAt, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("expiresAt", NOW.plusSeconds(604_800))
                .param("now", NOW)
                .update();
        List.of(
                "10000000-0000-0000-0000-000000000001",
                "10000000-0000-0000-0000-000000000003",
                "10000000-0000-0000-0000-000000000004"
        ).forEach(userId -> jdbc.sql("""
                        INSERT INTO team_follows (
                            team_id, user_id, created_at
                        ) VALUES (
                            '20000000-0000-0000-0000-000000000001',
                            :userId, :now
                        )
                        ON DUPLICATE KEY UPDATE team_id = team_id
                        """)
                .param("userId", userId)
                .param("now", NOW)
                .update());
        jdbc.sql("""
                INSERT INTO team_follow_counters (
                    team_id, follower_count, updated_at
                ) VALUES (
                    '20000000-0000-0000-0000-000000000001',
                    3, :now
                )
                ON DUPLICATE KEY UPDATE
                    follower_count = VALUES(follower_count),
                    updated_at = VALUES(updated_at)
                """)
                .param("now", NOW)
                .update();
    }

    private void seedMembership(
            String userId,
            String role,
            String permission
    ) {
        jdbc.sql("""
                INSERT INTO team_memberships (
                    team_id, user_id, role, permissions, state,
                    joined_at, updated_at, version
                ) VALUES (
                    '20000000-0000-0000-0000-000000000001',
                    :userId, :role, JSON_ARRAY(:permission), 'ACTIVE',
                    :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE team_id = team_id
                """)
                .param("userId", userId)
                .param("role", role)
                .param("permission", permission)
                .param("now", NOW)
                .update();
    }

    private void seedCategories() {
        seedCategory(
                "30000000-0000-0000-0000-000000000001",
                "ky-ao-viet",
                "Kỳ ảo Việt",
                "Huyền tích, tín ngưỡng và những miền đất Việt.",
                1
        );
        seedCategory(
                "30000000-0000-0000-0000-000000000002",
                "trinh-tham",
                "Trinh thám",
                "Bí ẩn, điều tra và những sự thật bị che giấu.",
                2
        );
        seedCategory(
                "30000000-0000-0000-0000-000000000003",
                "doi-thuong",
                "Đời thường",
                "Những câu chuyện gần gũi về người và phố.",
                3
        );
        seedCategory(
                "30000000-0000-0000-0000-000000000004",
                "lich-su",
                "Lịch sử",
                "Những lát cắt lịch sử được kể lại qua số phận con người.",
                4
        );
        seedCategory(
                "30000000-0000-0000-0000-000000000005",
                "khoa-hoc-vien-tuong",
                "Khoa học viễn tưởng",
                "Tương lai, công nghệ và lựa chọn của con người.",
                5
        );
        seedCategory(
                "30000000-0000-0000-0000-000000000006",
                "lang-man",
                "Lãng mạn",
                "Những mối quan hệ trưởng thành qua thời gian.",
                6
        );
        seedCategory(
                "30000000-0000-0000-0000-000000000007",
                "phieu-luu",
                "Phiêu lưu",
                "Những chuyến đi qua vùng đất lạ và thử thách mới.",
                7
        );
        seedCategory(
                "30000000-0000-0000-0000-000000000008",
                "kinh-di",
                "Kinh dị",
                "Nỗi sợ len vào những điều quen thuộc nhất.",
                8
        );
    }

    private void seedCategory(
            String id,
            String slug,
            String name,
            String description,
            int order
    ) {
        jdbc.sql("""
                INSERT INTO categories (
                    id, slug, name, description, group_key, group_order,
                    sort_order, active, version
                ) VALUES (
                    :id, :slug, :name, :description,
                    'GENRE', 10, :displayOrder, TRUE, 1
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", id)
                .param("slug", slug)
                .param("name", name)
                .param("description", description)
                .param("displayOrder", order)
                .update();
    }

    private void seedStoriesAndChapters() {
        seedStory(
                "40000000-0000-0000-0000-000000000001",
                "nguoi-giu-den-ben-song",
                "Người Giữ Đèn Bên Sông",
                "Mỗi đêm nước lớn, một ngọn đèn lại xuất hiện giữa dòng "
                        + "và gọi đúng tên những người đã quên lời hẹn.",
                "Lam Dạ",
                "PUBLISHED",
                "30000000-0000-0000-0000-000000000001"
        );
        seedStory(
                "40000000-0000-0000-0000-000000000002",
                "ho-so-can-phong-so-bay",
                "Hồ Sơ Căn Phòng Số Bảy",
                "Một biên tập viên nhận được bản thảo mô tả chính xác "
                        + "những vụ việc chưa từng xảy ra.",
                "Lam Dạ",
                "PUBLISHED",
                "30000000-0000-0000-0000-000000000002"
        );
        seedStory(
                "40000000-0000-0000-0000-000000000003",
                "mua-qua-ngo-nho",
                "Mưa Qua Ngõ Nhỏ",
                "Ba người hàng xóm cùng học cách bắt đầu lại sau một mùa mưa.",
                "Lam Dạ",
                "DRAFT",
                "30000000-0000-0000-0000-000000000003"
        );

        seedChapter(
                "50000000-0000-0000-0000-000000000001",
                "40000000-0000-0000-0000-000000000001",
                1,
                "Ngọn đèn trên mặt nước",
                "<p>Đêm ấy, con sông không phản chiếu ánh trăng.</p>"
                        + "<p>Chỉ có một ngọn đèn trôi ngược dòng, chậm rãi "
                        + "như đang tìm đường về một ký ức cũ.</p>",
                "PUBLISHED"
        );
        seedChapter(
                "50000000-0000-0000-0000-000000000002",
                "40000000-0000-0000-0000-000000000001",
                2,
                "Tên người trong gió",
                "<p>Gió từ bãi bồi mang theo một cái tên không ai muốn nhắc.</p>",
                "PUBLISHED"
        );
        seedChapter(
                "50000000-0000-0000-0000-000000000003",
                "40000000-0000-0000-0000-000000000002",
                1,
                "Bản thảo không người gửi",
                "<p>Phong bì nằm giữa bàn, khô ráo dù ngoài trời mưa trắng phố.</p>",
                "PUBLISHED"
        );
    }

    private void seedStory(
            String id,
            String slug,
            String title,
            String synopsis,
            String author,
            String state,
            String categoryId
    ) {
        jdbc.sql("""
                INSERT INTO stories (
                    id, team_id, slug, title, synopsis, author_name,
                    origin, language, completion_status, workflow_status,
                    current_revision, published_at,
                    created_at, updated_at, version
                ) VALUES (
                    :id, '20000000-0000-0000-0000-000000000001',
                    :slug, :title, :synopsis, :author,
                    'ORIGINAL', 'vi', 'ONGOING', :state,
                    :revisionId,
                    CASE WHEN :state = 'PUBLISHED' THEN :now ELSE NULL END,
                    :now, :now, 1
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", id)
                .param("slug", slug)
                .param("title", title)
                .param("synopsis", synopsis)
                .param("author", author)
                .param("state", state)
                .param(
                        "revisionId",
                        revisionIdForStory(id)
                )
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO story_categories (story_id, category_id)
                VALUES (:storyId, :categoryId)
                ON DUPLICATE KEY UPDATE story_id = story_id
                """)
                .param("storyId", id)
                .param("categoryId", categoryId)
                .update();
        jdbc.sql("""
                INSERT INTO story_aliases (story_id, alias)
                VALUES (:storyId, :alias)
                ON DUPLICATE KEY UPDATE story_id = story_id
                """)
                .param("storyId", id)
                .param("alias", title + " - truyện dài")
                .update();
    }

    private void seedExpandedLibrary() {
        List<StorySeed> stories = List.of(
                new StorySeed(
                        10, "ga-tau-mua-ha", "Ga Tàu Mùa Hạ",
                        "Một sân ga chỉ xuất hiện vào những ngày nóng nhất.",
                        3, false
                ),
                new StorySeed(
                        11, "moc-ban-trieu-nguyen", "Mộc Bản Triều Nguyễn",
                        "Người phục chế trẻ tìm thấy lời nhắn trong mộc bản.",
                        4, true
                ),
                new StorySeed(
                        12, "tram-quan-sat-so-khong",
                        "Trạm Quan Sát Số Không",
                        "Tín hiệu từ quỹ đạo kể về một Trái Đất khác.",
                        5, false
                ),
                new StorySeed(
                        13, "thu-gui-tu-da-lat", "Thư Gửi Từ Đà Lạt",
                        "Hai người xa lạ trò chuyện qua những lá thư cũ.",
                        6, true
                ),
                new StorySeed(
                        14, "ban-do-muoi-hai-cua-bien",
                        "Bản Đồ Mười Hai Cửa Biển",
                        "Một thủy thủ đi tìm hòn đảo biến mất khỏi bản đồ.",
                        7, false
                ),
                new StorySeed(
                        15, "tieng-go-cua-luc-ba-gio",
                        "Tiếng Gõ Cửa Lúc Ba Giờ",
                        "Căn hộ tầng mười ba luôn có khách sau nửa đêm.",
                        8, true
                ),
                new StorySeed(
                        16, "nguoi-ve-bong-tren-tuong",
                        "Người Vẽ Bóng Trên Tường",
                        "Mỗi bức chân dung hoàn thành lại xóa đi một ký ức.",
                        1, false
                ),
                new StorySeed(
                        17, "mat-ma-pho-co", "Mật Mã Phố Cổ",
                        "Những biển hiệu cũ dẫn tới một vụ án bị lãng quên.",
                        2, true
                ),
                new StorySeed(
                        18, "bep-lua-cuoi-ngon", "Bếp Lửa Cuối Ngõ",
                        "Một quán cơm nhỏ nối lại những gia đình xa cách.",
                        3, true
                )
        );
        stories.forEach(this::seedExpandedStory);
    }

    private void seedExpandedStory(StorySeed seed) {
        String storyId = seedId("40000000", seed.number());
        String categoryId = seedId("30000000", seed.category());
        seedStory(
                storyId,
                seed.slug(),
                seed.title(),
                seed.synopsis(),
                "Lam Dạ",
                "PUBLISHED",
                categoryId
        );
        if (seed.completed()) {
            jdbc.sql("""
                            UPDATE stories
                            SET completion_status = 'COMPLETED'
                            WHERE id = :id
                            """)
                    .param("id", storyId)
                    .update();
        }
        int firstChapter = seed.number() * 10;
        seedChapter(
                seedId("50000000", firstChapter),
                storyId,
                1,
                "Dấu hiệu đầu tiên",
                "<p>" + seed.synopsis() + "</p>"
                        + "<p>Câu chuyện bắt đầu khi thành phố vừa lên đèn."
                        + "</p>",
                "PUBLISHED"
        );
        seedChapter(
                seedId("50000000", firstChapter + 1),
                storyId,
                2,
                "Cánh cửa mở ra",
                "<p>Nhân vật chính bước qua ranh giới quen thuộc "
                        + "và nhận ra mọi lựa chọn đều để lại dấu vết.</p>",
                "PUBLISHED"
        );
        jdbc.sql("""
                        INSERT INTO comments (
                            id, story_id, chapter_id, user_id, body, state,
                            created_at, updated_at, version
                        ) VALUES (
                            :id, :storyId, :chapterId,
                            '10000000-0000-0000-0000-000000000001',
                            :body, 'VISIBLE', :now, :now, 0
                        )
                        ON DUPLICATE KEY UPDATE id = id
                        """)
                .param("id", seedId("60000000", seed.number()))
                .param("storyId", storyId)
                .param("chapterId", seedId("50000000", firstChapter))
                .param(
                        "body",
                        "Mở đầu của “" + seed.title()
                                + "” tạo không khí rất cuốn."
                )
                .param("now", NOW)
                .update();
        jdbc.sql("""
                        INSERT INTO notifications (
                            id, user_id, type, title, body,
                            target_url, created_at
                        ) VALUES (
                            :id,
                            '10000000-0000-0000-0000-000000000001',
                            'CHAPTER_PUBLISHED', 'Có chương mới',
                            :body, :targetUrl, :now
                        )
                        ON DUPLICATE KEY UPDATE id = id
                        """)
                .param("id", seedId("70000000", seed.number()))
                .param("body", seed.title() + " vừa có chương mới.")
                .param("targetUrl", "/stories/" + seed.slug())
                .param("now", NOW)
                .update();
    }

    private void seedChapter(
            String id,
            String storyId,
            int number,
            String title,
            String content,
            String state
    ) {
        jdbc.sql("""
                INSERT INTO chapters (
                    id, story_id, team_id, chapter_number, slug, title,
                    workflow_status, current_revision, published_at,
                    created_at, updated_at, version
                ) VALUES (
                    :id, :storyId,
                    '20000000-0000-0000-0000-000000000001',
                    :chapterNumber, :slug, :title, :state, :revisionId,
                    :now, :now, :now, 1
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", id)
                .param("storyId", storyId)
                .param("chapterNumber", number)
                .param("slug", "chuong-" + number)
                .param("title", title)
                .param("state", state)
                .param("revisionId", revisionIdForChapter(id))
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO chapter_revisions (
                    id, chapter_id, revision_no, content_html,
                    plain_text, checksum, created_at
                ) VALUES (
                    :id, :chapterId, 1, :content, :plainText,
                    :checksum, :createdAt
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", revisionIdForChapter(id))
                .param("chapterId", id)
                .param("content", content)
                .param("plainText", content.replaceAll("<[^>]+>", ""))
                .param("checksum", "seed-" + id)
                .param("createdAt", NOW)
                .update();
    }

    private void seedReaderExperience() {
        jdbc.sql("""
                INSERT INTO reading_progress (
                    user_id, story_id, chapter_id, position,
                    device_updated_at, updated_at, version
                ) VALUES (
                    '10000000-0000-0000-0000-000000000001',
                    '40000000-0000-0000-0000-000000000001',
                    '50000000-0000-0000-0000-000000000001',
                    0.685, :now, :now, 1
                )
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO comments (
                    id, story_id, chapter_id, user_id, body, state,
                    created_at, updated_at, version
                ) VALUES (
                    '60000000-0000-0000-0000-000000000001',
                    '40000000-0000-0000-0000-000000000001',
                    '50000000-0000-0000-0000-000000000001',
                    '10000000-0000-0000-0000-000000000001',
                    'Không khí chương đầu rất cuốn, nhất là chi tiết ngọn đèn.',
                    'VISIBLE', :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO notifications (
                    id, user_id, type, title, body, target_url, created_at
                ) VALUES (
                    '70000000-0000-0000-0000-000000000001',
                    '10000000-0000-0000-0000-000000000001',
                    'CHAPTER_PUBLISHED', 'Có chương mới',
                    'Người Giữ Đèn Bên Sông vừa có chương mới.',
                    '/stories/nguoi-giu-den-ben-song', :now
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("now", NOW)
                .update();
    }

    private void seedModerationAndWallet() {
        jdbc.sql("""
                INSERT INTO moderation_cases (
                    id, target_type, target_id, case_type, state, priority,
                    summary, created_at, updated_at, version
                ) VALUES (
                    '80000000-0000-0000-0000-000000000001',
                    'STORY', '40000000-0000-0000-0000-000000000003',
                    'PUBLISHING', 'OPEN', 80,
                    'Bản thảo mới đang chờ kiểm duyệt trước khi xuất bản.',
                    :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO wallets (
                    user_id, available_xu, pending_xu, updated_at, version
                ) VALUES (
                    '10000000-0000-0000-0000-000000000001',
                    25000, 0, :now, 0
                )
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO wallets (
                    user_id, available_xu, pending_xu, updated_at, version
                ) VALUES (
                    '10000000-0000-0000-0000-000000000002',
                    185000, 12000, :now, 0
                )
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("now", NOW)
                .update();
    }

    private void seedFunctionalScenarios() {
        seedReadingSessions();
        seedLedgerEntries();
        seedAdditionalModerationCases();
    }

    private void seedReadingSessions() {
        jdbc.sql("""
                INSERT INTO reading_sessions (
                    id, story_id, chapter_id, actor_type, actor_ref,
                    started_at, expires_at, purge_at, status,
                    last_sequence, updated_at
                ) VALUES (
                    'a0000000-0000-0000-0000-000000000001',
                    '40000000-0000-0000-0000-000000000001',
                    '50000000-0000-0000-0000-000000000001',
                    'USER',
                    '10000000-0000-0000-0000-000000000001',
                    :startedAt, :expiresAt, :purgeAt, 'ACTIVE', 4, :now
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("startedAt", NOW.minusSeconds(600))
                .param("expiresAt", NOW.plusSeconds(1_200))
                .param("purgeAt", NOW.plusSeconds(86_400))
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO reading_sessions (
                    id, story_id, chapter_id, actor_type, actor_ref,
                    started_at, expires_at, purge_at, status,
                    last_sequence, completion_id, completed_at, updated_at
                ) VALUES (
                    'a0000000-0000-0000-0000-000000000002',
                    '40000000-0000-0000-0000-000000000002',
                    '50000000-0000-0000-0000-000000000003',
                    'USER',
                    '10000000-0000-0000-0000-000000000001',
                    :startedAt, :expiresAt, :purgeAt, 'COMPLETED', 8,
                    'a1000000-0000-0000-0000-000000000002',
                    :completedAt, :now
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("startedAt", NOW.minusSeconds(7_200))
                .param("expiresAt", NOW.minusSeconds(3_600))
                .param("purgeAt", NOW.plusSeconds(82_800))
                .param("completedAt", NOW.minusSeconds(6_300))
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO reading_session_batches (
                    session_id, batch_id, processed_at
                ) VALUES (
                    'a0000000-0000-0000-0000-000000000001',
                    'a2000000-0000-0000-0000-000000000001',
                    :now
                )
                ON DUPLICATE KEY UPDATE session_id = session_id
                """)
                .param("now", NOW)
                .update();
    }

    private void seedLedgerEntries() {
        seedLedgerEntry(
                1,
                "10000000-0000-0000-0000-000000000001",
                "TOPUP",
                30_000,
                "TOPUP",
                "seed-topup-1",
                "Nạp XU thử nghiệm"
        );
        seedLedgerEntry(
                2,
                "10000000-0000-0000-0000-000000000001",
                "DONATION",
                -5_000,
                "STORY",
                "40000000-0000-0000-0000-000000000001",
                "Ủng hộ tác giả Lam Dạ"
        );
        seedLedgerEntry(
                3,
                "10000000-0000-0000-0000-000000000002",
                "REWARD",
                20_000,
                "STORY",
                "40000000-0000-0000-0000-000000000001",
                "Thưởng lượt đọc hợp lệ"
        );
        seedLedgerEntry(
                4,
                "10000000-0000-0000-0000-000000000002",
                "DONATION",
                5_000,
                "STORY",
                "40000000-0000-0000-0000-000000000001",
                "Nhận ủng hộ từ độc giả"
        );
    }

    private void seedLedgerEntry(
            int number,
            String userId,
            String entryType,
            long amount,
            String referenceType,
            String referenceId,
            String description
    ) {
        jdbc.sql("""
                INSERT INTO ledger_entries (
                    id, user_id, entry_type, amount_xu, reference_type,
                    reference_id, description, created_at
                ) VALUES (
                    :id, :userId, :entryType, :amount,
                    :referenceType, :referenceId, :description, :createdAt
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", seedId("b0000000", number))
                .param("userId", userId)
                .param("entryType", entryType)
                .param("amount", amount)
                .param("referenceType", referenceType)
                .param("referenceId", referenceId)
                .param("description", description)
                .param("createdAt", NOW.minusSeconds(number * 3_600L))
                .update();
    }

    private void seedAdditionalModerationCases() {
        seedModerationCase(
                2,
                "COMMENT",
                "60000000-0000-0000-0000-000000000001",
                "COMMUNITY",
                60,
                "Bình luận được báo cáo để kiểm tra ngôn từ."
        );
        seedModerationCase(
                3,
                "STORY",
                "40000000-0000-0000-0000-000000000002",
                "COPYRIGHT",
                90,
                "Yêu cầu đối chiếu quyền sử dụng bản thảo."
        );
        seedModerationCase(
                4,
                "CHAPTER",
                "50000000-0000-0000-0000-000000000002",
                "PUBLISHING",
                50,
                "Chương đã chỉnh sửa đang chờ duyệt lại."
        );
    }

    private void seedModerationCase(
            int number,
            String targetType,
            String targetId,
            String caseType,
            int priority,
            String summary
    ) {
        jdbc.sql("""
                INSERT INTO moderation_cases (
                    id, target_type, target_id, case_type, state, priority,
                    summary, created_at, updated_at, version
                ) VALUES (
                    :id, :targetType, :targetId, :caseType,
                    'OPEN', :priority, :summary, :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", seedId("80000000", number))
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("caseType", caseType)
                .param("priority", priority)
                .param("summary", summary)
                .param("now", NOW)
                .update();
    }

    private static String revisionIdForStory(String storyId) {
        return "90000000-0000-0000-0000-"
                + storyId.substring(storyId.length() - 12);
    }

    private static String revisionIdForChapter(String chapterId) {
        return "91000000-0000-0000-0000-"
                + chapterId.substring(chapterId.length() - 12);
    }

    private static String seedId(String prefix, int number) {
        return prefix + "-0000-0000-0000-"
                + String.format("%012d", number);
    }

    private record StorySeed(
            int number,
            String slug,
            String title,
            String synopsis,
            int category,
            boolean completed
    ) {
    }
}
