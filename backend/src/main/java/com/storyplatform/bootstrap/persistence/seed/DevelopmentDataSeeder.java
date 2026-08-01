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
        seedUser(
                "10000000-0000-0000-0000-000000000005",
                "neon.team@gioitruyen.local",
                "team123456",
                "Neon Team",
                "Nhóm dịch nội dung đô thị, công nghệ và lãng mạn hiện đại.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000006",
                "nha.la@gioitruyen.local",
                "team123456",
                "Nhà Lá Studio",
                "Studio biên tập các series học đường, chữa lành và đời thường.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000007",
                "metro9@gioitruyen.local",
                "team123456",
                "Metro Tuyến 9",
                "Team mới đăng ký, đang chờ admin duyệt hồ sơ xuất bản.",
                List.of("USER")
        );

        seedAdditionalUsers();
        seedTeamAndMembership();
        seedAdditionalTeams();
        seedCategories();
        seedStoriesAndChapters();
        seedExpandedLibrary();
        seedStoryDiscoveryMetadata();
        seedHomePromotionBookings();
        seedReaderExperience();
        seedModerationAndWallet();
        seedPaidReadingExperience();
        seedFunctionalScenarios();
    }

    private void seedAdditionalUsers() {
        seedUser(
                "10000000-0000-0000-0000-000000000008",
                "minh.an@gioitruyen.local",
                "reader123456",
                "Minh An",
                "Đọc truyện trinh thám và lưu lại những bộ đang theo dõi.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000009",
                "thao.nguyen@gioitruyen.local",
                "reader123456",
                "Thảo Nguyên",
                "Yêu truyện đời thường, lãng mạn và những chương audio buổi tối.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000010",
                "bao.chau@gioitruyen.local",
                "team123456",
                "Bảo Châu",
                "Biên tập viên truyện sáng tác hiện đại.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000011",
                "quang.huy@gioitruyen.local",
                "reader123456",
                "Quang Huy",
                "Theo dõi truyện khoa học viễn tưởng và phiêu lưu.",
                List.of("USER")
        );
        seedUser(
                "10000000-0000-0000-0000-000000000012",
                "linh.chi@gioitruyen.local",
                "team123456",
                "Linh Chi",
                "Dịch giả và hiệu đính nội dung lãng mạn.",
                List.of("USER")
        );
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
        seedTeam(
                "20000000-0000-0000-0000-000000000002",
                "neon-team",
                "Neon Team",
                "Nhóm dịch chuyên truyện đô thị, công nghệ và đời sống sáng tạo.",
                "10000000-0000-0000-0000-000000000005",
                "ACTIVE",
                12
        );
        seedTeam(
                "20000000-0000-0000-0000-000000000003",
                "nha-la-studio",
                "Nhà Lá Studio",
                "Studio biên tập các nội dung học đường, gia đình và chữa lành.",
                "10000000-0000-0000-0000-000000000006",
                "ACTIVE",
                8
        );
        seedTeam(
                "20000000-0000-0000-0000-000000000004",
                "metro-tuyen-9",
                "Metro Tuyến 9",
                "Team đăng ký mới, đang chờ admin xem xét quyền đăng truyện.",
                "10000000-0000-0000-0000-000000000007",
                "PENDING_REVIEW",
                0
        );
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
                INSERT INTO team_applications (
                    id, requester_user_id, slug, name, description,
                    state, submitted_at, version
                ) VALUES (
                    '23000000-0000-0000-0000-000000000001',
                    '10000000-0000-0000-0000-000000000007',
                    'metro-tuyen-9-moi',
                    'Metro Tuyến 9 Mới',
                    'Hồ sơ đăng ký team mới đang chờ admin duyệt quyền đăng truyện.',
                    'PENDING_REVIEW',
                    :now,
                    0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("now", NOW)
                .update();
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

    private void seedAdditionalTeams() {
        seedTeam(
                "20000000-0000-0000-0000-000000000005",
                "tram-may-studio",
                "Trạm Mây Studio",
                "Nhóm biên tập truyện kỳ ảo và khoa học viễn tưởng dành cho độc giả trẻ.",
                "10000000-0000-0000-0000-000000000010",
                "ACTIVE",
                1_284
        );
        seedTeam(
                "20000000-0000-0000-0000-000000000006",
                "mot-chuong-nua",
                "Một Chương Nữa",
                "Nhóm dịch truyện lãng mạn, đời thường với lịch cập nhật đều mỗi tuần.",
                "10000000-0000-0000-0000-000000000012",
                "ACTIVE",
                936
        );
    }

    private void seedTeam(
            String id,
            String slug,
            String name,
            String description,
            String ownerUserId,
            String state,
            long followerCount
    ) {
        jdbc.sql("""
                INSERT INTO teams (
                    id, slug, name, description, owner_user_id, state,
                    created_at, updated_at, version
                ) VALUES (
                    :id, :slug, :name, :description, :ownerUserId, :state,
                    :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", id)
                .param("slug", slug)
                .param("name", name)
                .param("description", description)
                .param("ownerUserId", ownerUserId)
                .param("state", state)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO team_memberships (
                    team_id, user_id, role, permissions, state,
                    joined_at, updated_at, version
                ) VALUES (
                    :teamId, :ownerUserId, 'OWNER',
                    JSON_ARRAY('STORY_CREATE', 'STORY_EDIT', 'MEMBER_MANAGE'),
                    'ACTIVE', :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE team_id = team_id
                """)
                .param("teamId", id)
                .param("ownerUserId", ownerUserId)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO team_follow_counters (
                    team_id, follower_count, updated_at
                ) VALUES (
                    :teamId, :followerCount, :now
                )
                ON DUPLICATE KEY UPDATE
                    follower_count = VALUES(follower_count),
                    updated_at = VALUES(updated_at)
                """)
                .param("teamId", id)
                .param("followerCount", followerCount)
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
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    description = VALUES(description),
                    sort_order = VALUES(sort_order),
                    active = TRUE
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
        for (int number = 3; number <= 24; number++) {
            seedChapter(
                    seedId("50010000", 1_000 + number),
                    "40000000-0000-0000-0000-000000000001",
                    number,
                    "Nhịp đèn thứ " + String.format("%02d", number),
                    reviewChapterContent(number),
                    "PUBLISHED"
            );
        }
        seedChapter(
                "50000000-0000-0000-0000-000000000003",
                "40000000-0000-0000-0000-000000000002",
                1,
                "Bản thảo không người gửi",
                "<p>Phong bì nằm giữa bàn, khô ráo dù ngoài trời mưa trắng phố.</p>",
                "PUBLISHED"
        );
    }

    private static String reviewChapterContent(int number) {
        return """
                <p>Chương %02d mở ra một nhịp đọc dài hơn để kiểm tra giao diện reader, khoảng cách dòng và điều hướng chương kế tiếp.</p>
                <p>Người giữ đèn đi dọc bờ sông, ghi lại từng tín hiệu nhỏ trong tiếng nước và ánh sáng lam nhạt phía cuối bến.</p>
                <p>Khi chuông đồng hồ điểm nửa đêm, những mảnh ký ức cũ hiện lên theo thứ tự khác nhau, đủ dài để kiểm tra trạng thái cuộn trang.</p>
                <p>Ở cuối chương, nhân vật nhận ra ngọn đèn không chỉ dẫn đường cho người mất lối mà còn lưu lại lời hứa của cả thị trấn.</p>
                """.formatted(number);
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
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    synopsis = VALUES(synopsis),
                    author_name = VALUES(author_name),
                    workflow_status = VALUES(workflow_status),
                    updated_at = VALUES(updated_at)
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
                ),
                new StorySeed(19, "startup-duoi-mua-neon", "Startup Dưới Mưa Neon",
                        "Một nhà sáng lập trẻ đứng trước lựa chọn giữa tăng trưởng và lời hứa với đội ngũ.",
                        5, false),
                new StorySeed(20, "hop-dong-hon-nhan-30-ngay", "Hợp Đồng Hôn Nhân 30 Ngày",
                        "Hai người xa lạ ký hợp đồng giả nhưng lại gặp nhau đúng lúc cần một mái nhà.",
                        6, true),
                new StorySeed(21, "thanh-pho-khong-ngu", "Thành Phố Không Ngủ",
                        "Một shipper đêm phát hiện các tòa nhà đang gửi tin nhắn bằng ánh đèn.",
                        2, false),
                new StorySeed(22, "idol-o-tang-thuong", "Idol Ở Tầng Thượng",
                        "Cô gái thực tập sinh sống lại một mùa debut để cứu nhóm nhạc đang tan rã.",
                        1, false),
                new StorySeed(23, "quan-ca-phe-sau-nua-dem", "Quán Cà Phê Sau Nửa Đêm",
                        "Quán cà phê chỉ mở cửa cho những người đang cần sửa lại một lời tạm biệt.",
                        3, true),
                new StorySeed(24, "ai-viet-thu-tinh", "AI Viết Thư Tình",
                        "Lập trình viên tạo chatbot giúp khách hàng tỏ tình và nhận ra nó hiểu mình quá rõ.",
                        5, false),
                new StorySeed(25, "metro-tuyen-so-9", "Metro Tuyến Số 9",
                        "Chuyến tàu cuối ngày đưa hành khách đến các quyết định họ từng bỏ lỡ.",
                        7, false),
                new StorySeed(26, "can-ho-co-cua-so-mau-xanh", "Căn Hộ Có Cửa Sổ Màu Xanh",
                        "Một nhà thiết kế nội thất nghe thấy câu chuyện của chủ nhà qua màu sơn trên tường.",
                        6, true),
                new StorySeed(27, "livestream-luc-0-gio", "Livestream Lúc 0 Giờ",
                        "Streamer trinh thám bắt gặp một vụ án đang diễn ra trong bình luận trực tiếp.",
                        2, false),
                new StorySeed(28, "bau-troi-sau-bien-quang-cao", "Bầu Trời Sau Biển Quảng Cáo",
                        "Một biển quảng cáo lỗi pixel mở ra nhật ký của người mất tích.",
                        8, false),
                new StorySeed(29, "doi-thu-ngoi-ban-ben", "Đối Thủ Ngồi Bàn Bên",
                        "Hai học sinh đứng đầu bảng điểm bắt đầu hợp tác để chống lại một cuộc thi bất công.",
                        4, true),
                new StorySeed(30, "van-phong-tang-18", "Văn Phòng Tầng 18",
                        "Nhân viên mới nhận ra tầng 18 của công ty không tồn tại trên bản vẽ.",
                        8, false),
                new StorySeed(31, "bao-tang-ky-uc-so", "Bảo Tàng Ký Ức Số",
                        "Một giám tuyển số hóa ký ức của người lạ và tìm thấy ký ức của chính mình.",
                        5, true),
                new StorySeed(32, "nhom-chat-gia-dinh", "Nhóm Chat Gia Đình",
                        "Những tin nhắn bị xóa trong nhóm chat làm lộ ra bí mật của ba thế hệ.",
                        3, true),
                new StorySeed(33, "duong-chay-5-gio-sang", "Đường Chạy 5 Giờ Sáng",
                        "Một vận động viên phong trào gặp lại người đã thay đổi cuộc đời mình trên đường chạy.",
                        7, false),
                new StorySeed(34, "phong-thu-am-so-404", "Phòng Thu Âm Số 404",
                        "Bản demo của một ca khúc chưa phát hành dự đoán chính xác tin tức ngày mai.",
                        1, false),
                new StorySeed(35, "nguoi-thu-vien-cuoi-tuan", "Người Thủ Viện Cuối Tuần",
                        "Thủ thư bán thời gian giúp độc giả tìm đúng quyển sách cần cho ngày mai.",
                        4, true),
                new StorySeed(36, "ung-dung-hen-ho-vo-danh", "Ứng Dụng Hẹn Hò Vô Danh",
                        "Một ứng dụng ghép đôi đưa hai người qua các nhiệm vụ không được biết tên nhau.",
                        6, false)
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
        if (seed.number() == 18) {
            List<CuratedOriginalStory.Chapter> chapters =
                    CuratedOriginalStory.chapters();
            for (int index = 0; index < chapters.size(); index++) {
                CuratedOriginalStory.Chapter chapter = chapters.get(index);
                seedChapter(
                        seedId("50000000", firstChapter + index),
                        storyId,
                        index + 1,
                        chapter.title(),
                        chapter.contentHtml(),
                        "PUBLISHED"
                );
            }
        } else {
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
            for (int chapterNumber = 3; chapterNumber <= 8; chapterNumber++) {
                seedChapter(
                        seedId(
                                "50000000",
                                firstChapter + chapterNumber - 1
                        ),
                        storyId,
                        chapterNumber,
                        expandedChapterTitle(chapterNumber),
                        expandedChapterContent(seed, chapterNumber),
                        "PUBLISHED"
                );
            }
        }
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

    private static String expandedChapterTitle(int chapterNumber) {
        return switch (chapterNumber) {
            case 3 -> "Tin nhắn chưa gửi";
            case 4 -> "Cuộc hẹn dưới mưa";
            case 5 -> "Điều còn giấu kín";
            case 6 -> "Lựa chọn lúc nửa đêm";
            case 7 -> "Khi thành phố thức giấc";
            default -> "Một khởi đầu khác";
        };
    }

    private static String expandedChapterContent(
            StorySeed seed,
            int chapterNumber
    ) {
        return """
                <p>%s</p>
                <p>Ở chương %d, các nhân vật phải đối diện với hệ quả từ lựa chọn trước đó. Những chi tiết nhỏ dần nối lại thành một câu trả lời rõ ràng hơn.</p>
                <p>Không gian thành phố thay đổi theo từng khung giờ, còn cuộc trò chuyện dang dở buộc mọi người phải thành thật với điều mình thực sự mong muốn.</p>
                <p>Chương khép lại bằng một phát hiện mới, mở đường cho mạch truyện tiếp theo.</p>
                """.formatted(seed.synopsis(), chapterNumber);
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
                ON DUPLICATE KEY UPDATE
                    slug = VALUES(slug),
                    title = VALUES(title),
                    workflow_status = VALUES(workflow_status),
                    current_revision = VALUES(current_revision),
                    updated_at = VALUES(updated_at)
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
                ON DUPLICATE KEY UPDATE
                    content_html = VALUES(content_html),
                    plain_text = VALUES(plain_text),
                    checksum = VALUES(checksum)
                """)
                .param("id", revisionIdForChapter(id))
                .param("chapterId", id)
                .param("content", content)
                .param("plainText", content.replaceAll("<[^>]+>", ""))
                .param("checksum", "seed-" + id)
                .param("createdAt", NOW)
                .update();
    }

    private void seedHomePromotionBookings() {
        List<String> storyIds = List.of(
                "40000000-0000-0000-0000-000000000001",
                seedId("40000000", 18),
                seedId("40000000", 17),
                seedId("40000000", 16),
                seedId("40000000", 15),
                seedId("40000000", 14),
                seedId("40000000", 13),
                seedId("40000000", 12),
                seedId("40000000", 11),
                seedId("40000000", 10),
                seedId("40000000", 20),
                seedId("40000000", 19)
        );
        for (int index = 0; index < storyIds.size(); index++) {
            seedPromotionBooking(index + 1, storyIds.get(index));
        }
    }

    private void seedStoryDiscoveryMetadata() {
        List<Integer> storyNumbers = List.of(
                1, 2, 3,
                10, 11, 12, 13, 14, 15, 16, 17, 18,
                19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
                29, 30, 31, 32, 33, 34, 35, 36
        );
        List<Integer> exclusiveStories = List.of(
                1, 11, 13, 15, 17, 18, 20, 23, 26, 29, 31, 32, 35
        );
        List<Integer> newReleaseStories = List.of(
                18, 17, 16, 15, 14, 13, 12, 11, 10, 19, 20, 21
        );
        List<Integer> recentUpdateStories = List.of(
                1, 18, 17, 16, 15, 14, 13, 12, 11, 10, 22, 23
        );
        List<Integer> originalStories = List.of(
                1, 2, 3, 10, 13, 18, 20, 22, 23, 24, 26, 29
        );
        List<Integer> completedStories = List.of(
                11, 13, 15, 17, 18, 20, 23, 26, 29, 31, 32, 35
        );

        storyNumbers.forEach(number -> seedRevenuePolicy(
                storyId(number),
                exclusiveStories.contains(number)
        ));
        newReleaseStories.forEach(number -> seedStoryLabel(storyId(number), "NEW_RELEASE"));
        recentUpdateStories.forEach(number -> seedStoryLabel(storyId(number), "RECENT_UPDATE"));
        originalStories.forEach(number -> seedStoryLabel(storyId(number), "ORIGINAL"));
        completedStories.forEach(number -> seedStoryLabel(storyId(number), "COMPLETED"));
        exclusiveStories.forEach(number -> seedStoryLabel(storyId(number), "EXCLUSIVE"));

        for (int index = 0; index < storyNumbers.size(); index++) {
            int storyNumber = storyNumbers.get(index);
            long base = 120_000L - (index * 2_400L);
            seedStoryMetrics(
                    storyId(storyNumber),
                    Math.max(7_500L, base + (storyNumber * 250L)),
                    Math.max(60L, 980L - (index * 22L) + storyNumber),
                    Math.max(20L, 420L - (index * 9L) + storyNumber),
                    Math.max(4_000L, 82_000L - (index * 1_700L) + (storyNumber * 310L))
            );
        }
    }

    private void seedStoryLabel(String storyId, String label) {
        jdbc.sql("""
                INSERT INTO story_labels (story_id, label, created_at)
                VALUES (:storyId, :label, :now)
                ON DUPLICATE KEY UPDATE story_id = story_id
                """)
                .param("storyId", storyId)
                .param("label", label)
                .param("now", NOW)
                .update();
    }

    private void seedRevenuePolicy(String storyId, boolean exclusive) {
        jdbc.sql("""
                INSERT INTO story_revenue_policies (
                    story_id, exclusive, author_share_bps, admin_share_bps,
                    updated_at, version
                ) VALUES (
                    :storyId, :exclusive, :authorShareBps, :adminShareBps,
                    :now, 0
                )
                ON DUPLICATE KEY UPDATE
                    exclusive = VALUES(exclusive),
                    author_share_bps = VALUES(author_share_bps),
                    admin_share_bps = VALUES(admin_share_bps),
                    updated_at = VALUES(updated_at)
                """)
                .param("storyId", storyId)
                .param("exclusive", exclusive)
                .param("authorShareBps", exclusive ? 9000 : 7000)
                .param("adminShareBps", exclusive ? 1000 : 3000)
                .param("now", NOW)
                .update();
    }

    private void seedStoryMetrics(
            String storyId,
            long donationXu,
            long recommendationCount,
            long saveCount,
            long viewCount
    ) {
        jdbc.sql("""
                INSERT INTO story_engagement_metrics (
                    story_id, donation_xu, recommendation_count, save_count,
                    view_count, updated_at
                ) VALUES (
                    :storyId, :donationXu, :recommendationCount, :saveCount,
                    :viewCount, :now
                )
                ON DUPLICATE KEY UPDATE
                    donation_xu = VALUES(donation_xu),
                    recommendation_count = VALUES(recommendation_count),
                    save_count = VALUES(save_count),
                    view_count = VALUES(view_count),
                    updated_at = VALUES(updated_at)
                """)
                .param("storyId", storyId)
                .param("donationXu", donationXu)
                .param("recommendationCount", recommendationCount)
                .param("saveCount", saveCount)
                .param("viewCount", viewCount)
                .param("now", NOW)
                .update();
    }

    private void seedPromotionBooking(int slot, String storyId) {
        long pricePerDay = 2_500L;
        int bookedDays = 7;
        long totalCost = pricePerDay * bookedDays;
        jdbc.sql("""
                INSERT INTO story_promotion_bookings (
                    id, story_id, team_id, slot_position, tag_label,
                    price_xu_per_day, booked_days, total_cost_xu,
                    starts_at, ends_at, state, created_at, updated_at, version
                ) VALUES (
                    :id, :storyId, '20000000-0000-0000-0000-000000000001',
                    :slot, 'Nổi bật', :pricePerDay, :bookedDays, :totalCost,
                    :startsAt, :endsAt, 'ACTIVE', :now, :now, 0
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", seedId("22000000", slot))
                .param("storyId", storyId)
                .param("slot", slot)
                .param("pricePerDay", pricePerDay)
                .param("bookedDays", bookedDays)
                .param("totalCost", totalCost)
                .param("startsAt", NOW.minusSeconds(86_400))
                .param("endsAt", NOW.plusSeconds(604_800))
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO ledger_entries (
                    id, user_id, entry_type, amount_xu,
                    reference_type, reference_id, description, created_at
                ) VALUES (
                    :id, '10000000-0000-0000-0000-000000000002',
                    'PROMOTION_BOOKING', :amountXu,
                    'STORY_PROMOTION_BOOKING', :bookingId,
                    :description, :now
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", seedId("72000000", slot))
                .param("amountXu", -totalCost)
                .param("bookingId", seedId("22000000", slot))
                .param("description", "Phí booking truyện lên trang đầu slot " + slot
                        + " trong " + bookedDays + " ngày")
                .param("now", NOW)
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
                    1250000, 0, :now, 0
                )
                ON DUPLICATE KEY UPDATE
                    available_xu = VALUES(available_xu),
                    pending_xu = VALUES(pending_xu),
                    updated_at = VALUES(updated_at)
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
        seedDailyDashboardSeries();
        seedAdditionalModerationCases();
    }

    private void seedPaidReadingExperience() {
        for (int chapterNumber = 5; chapterNumber <= 8; chapterNumber++) {
            String chapterId = seedId("50010000", 1_000 + chapterNumber);
            jdbc.sql("""
                    INSERT INTO chapter_prices (
                        chapter_id, price_xu, updated_at, version
                    ) VALUES (:chapterId, :priceXu, :now, 0)
                    ON DUPLICATE KEY UPDATE
                        price_xu = VALUES(price_xu),
                        updated_at = VALUES(updated_at)
                    """)
                    .param("chapterId", chapterId)
                    .param("priceXu", 100L + ((chapterNumber - 5L) * 25L))
                    .param("now", NOW)
                    .update();
        }
        List.of(1, 10, 13, 18, 24, 31).forEach(number -> jdbc.sql("""
                INSERT INTO story_library_entries (
                    user_id, story_id, saved_at
                ) VALUES (
                    '10000000-0000-0000-0000-000000000001',
                    :storyId, :now
                )
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("storyId", storyId(number))
                .param("now", NOW.minusSeconds(number * 900L))
                .update());
        String ledgerId = "b3000000-0000-0000-0000-000000000001";
        String chapterId = seedId("50010000", 1_005);
        jdbc.sql("""
                INSERT INTO ledger_entries (
                    id, user_id, entry_type, amount_xu, reference_type,
                    reference_id, description, created_at
                ) VALUES (
                    :id, '10000000-0000-0000-0000-000000000001',
                    'CHAPTER_UNLOCK', -100, 'CHAPTER', :chapterId,
                    'Mở khóa chương 5 - Người Giữ Đèn Bên Sông', :now
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", ledgerId)
                .param("chapterId", chapterId)
                .param("now", NOW)
                .update();
        jdbc.sql("""
                INSERT INTO chapter_unlocks (
                    user_id, chapter_id, price_xu,
                    ledger_entry_id, unlocked_at
                ) VALUES (
                    '10000000-0000-0000-0000-000000000001',
                    :chapterId, 100, :ledgerId, :now
                )
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("chapterId", chapterId)
                .param("ledgerId", ledgerId)
                .param("now", NOW)
                .update();
    }

    private void seedDailyDashboardSeries() {
        List<String> readerIds = List.of(
                "10000000-0000-0000-0000-000000000001",
                "10000000-0000-0000-0000-000000000008",
                "10000000-0000-0000-0000-000000000009",
                "10000000-0000-0000-0000-000000000011"
        );
        for (int day = 13; day >= 0; day--) {
            Instant dayStart = NOW.minusSeconds(day * 86_400L);
            int sessionCount = 4 + ((13 - day) % 6);
            for (int index = 0; index < sessionCount; index++) {
                String readerId = readerIds.get(index % readerIds.size());
                int storyNumber = 10 + ((day + index) % 20);
                int chapterNumber = storyNumber * 10;
                Instant startedAt = dayStart.plusSeconds(index * 1_200L);
                jdbc.sql("""
                        INSERT INTO reading_sessions (
                            id, story_id, chapter_id, actor_type, actor_ref,
                            started_at, expires_at, purge_at, status,
                            last_sequence, completed_at, updated_at
                        ) VALUES (
                            :id, :storyId, :chapterId, 'USER', :readerId,
                            :startedAt, :expiresAt, :purgeAt, 'COMPLETED',
                            8, :completedAt, :updatedAt
                        )
                        ON DUPLICATE KEY UPDATE id = id
                        """)
                        .param("id", seedId("a3000000", (day * 100) + index + 1))
                        .param("storyId", storyId(storyNumber))
                        .param("chapterId", seedId("50000000", chapterNumber))
                        .param("readerId", readerId)
                        .param("startedAt", startedAt)
                        .param("expiresAt", startedAt.plusSeconds(1_800))
                        .param("purgeAt", startedAt.plusSeconds(604_800))
                        .param("completedAt", startedAt.plusSeconds(900))
                        .param("updatedAt", startedAt.plusSeconds(900))
                        .update();
            }
            seedDailyRevenue(day, dayStart);
        }
    }

    private void seedDailyRevenue(int day, Instant createdAt) {
        long topupAmount = 45_000L + ((13L - day) * 8_500L);
        jdbc.sql("""
                INSERT INTO ledger_entries (
                    id, user_id, entry_type, amount_xu, reference_type,
                    reference_id, description, created_at
                ) VALUES (
                    :id, '10000000-0000-0000-0000-000000000001',
                    'TOPUP', :amountXu, 'TOPUP', :referenceId,
                    'Nạp XU qua chuyển khoản', :createdAt
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", seedId("b1000000", day + 1))
                .param("amountXu", topupAmount)
                .param("referenceId", "seed-daily-topup-" + day)
                .param("createdAt", createdAt.plusSeconds(3_600))
                .update();
        jdbc.sql("""
                INSERT INTO ledger_entries (
                    id, user_id, entry_type, amount_xu, reference_type,
                    reference_id, description, created_at
                ) VALUES (
                    :id, '10000000-0000-0000-0000-000000000002',
                    'DONATION', :amountXu, 'STORY', :storyId,
                    'Doanh thu ủng hộ truyện', :createdAt
                )
                ON DUPLICATE KEY UPDATE id = id
                """)
                .param("id", seedId("b2000000", day + 1))
                .param("amountXu", 5_000L + ((day % 5L) * 2_000L))
                .param("storyId", storyId(10 + (day % 20)))
                .param("createdAt", createdAt.plusSeconds(7_200))
                .update();
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

    private static String storyId(int number) {
        return seedId("40000000", number);
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
