package com.storyplatform.promotion.application;

import com.storyplatform.promotion.application.dto.PromotionDtos.CreatePromotionRequest;
import com.storyplatform.promotion.application.dto.PromotionDtos.ExtendPromotionRequest;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotableStory;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotionBooking;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotionOverview;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotionPackage;
import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sells home-page promotion slots ("bố cáo") to the team that owns a story.
 *
 * <p>A story can only be promoted once its team has actually published it, and a
 * single story may not hold more than {@value #MAX_TOTAL_DAYS} days of upcoming
 * airtime at a time — extensions stack onto the existing window rather than
 * replacing it.</p>
 */
@Service
public class PromotionService {

    /** Ceiling on how far into the future a story's promotion may run. */
    public static final int MAX_TOTAL_DAYS = 30;

    private static final String TRANSACTION_TYPE = "PURCHASE";
    private static final String REFERENCE_TYPE = "STORY_PROMOTION";

    /**
     * Columns every booking view needs. Kept in one place because
     * {@link #toBooking(ResultSet)} reads all of them and a query that forgot
     * one failed only at runtime.
     */
    private static final String BOOKING_SELECT = """
            SELECT p.id, p.story_id, s.title AS story_title, s.slug AS story_slug,
                   p.team_id, t.name AS team_name, p.duration_days, p.coin_paid,
                   p.starts_at, p.ends_at, p.status, p.review_note, p.reviewed_at,
                   p.created_at, u.email AS purchased_by_email
            FROM story_promotions p
            JOIN stories s ON s.id = p.story_id
            JOIN teams t ON t.id = p.team_id
            JOIN users u ON u.id = p.purchased_by
            """;

    private final JdbcClient jdbc;

    public PromotionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PromotionOverview overview(UUID userId) {
        return new PromotionOverview(
                packages(),
                promotableStories(userId),
                bookings(userId),
                walletBalance(userId),
                MAX_TOTAL_DAYS
        );
    }

    @Transactional(readOnly = true)
    public List<PromotionPackage> packages() {
        return jdbc.sql("""
                        SELECT id, code, name, duration_days, price_coin, description
                        FROM promotion_packages
                        WHERE is_active = TRUE
                        ORDER BY sort_order, duration_days
                        """)
                .query((rs, rowNum) -> new PromotionPackage(
                        rs.getString("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("duration_days"),
                        rs.getLong("price_coin"),
                        rs.getLong("price_coin") / Math.max(1, rs.getInt("duration_days")),
                        rs.getString("description")
                ))
                .list();
    }

    /**
     * Stories the caller may promote: published titles belonging to a team they
     * are an active member of. Admins see every published story.
     */
    @Transactional(readOnly = true)
    public List<PromotableStory> promotableStories(UUID userId) {
        String sql = """
                SELECT s.id, s.slug, s.title, s.cover_url, s.team_id, t.name AS team_name,
                       (SELECT MAX(p.ends_at) FROM story_promotions p
                         WHERE p.story_id = s.id AND p.status = 'ACTIVE' AND p.ends_at > NOW(3)) AS active_until
                FROM stories s
                JOIN teams t ON t.id = s.team_id
                WHERE s.status = 'PUBLISHED'
                """
                + (isAdmin(userId) ? "" : """
                  AND EXISTS (SELECT 1 FROM team_members m
                              WHERE m.team_id = s.team_id AND m.user_id = ? AND m.status = 'ACTIVE')
                """)
                + " ORDER BY s.updated_at DESC";

        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (!isAdmin(userId)) {
            spec = spec.param(userId.toString());
        }
        return spec.query((rs, rowNum) -> toPromotableStory(rs)).list();
    }

    @Transactional(readOnly = true)
    public List<PromotionBooking> bookings(UUID userId) {
        String sql = BOOKING_SELECT + """
                WHERE
                """
                + (isAdmin(userId) ? " 1 = 1 " : """
                  EXISTS (SELECT 1 FROM team_members m
                          WHERE m.team_id = p.team_id AND m.user_id = ? AND m.status = 'ACTIVE')
                """)
                + " ORDER BY p.created_at DESC LIMIT 100";

        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        if (!isAdmin(userId)) {
            spec = spec.param(userId.toString());
        }
        return spec.query((rs, rowNum) -> toBooking(rs)).list();
    }

    /* ----------------------------------------------------------- admin board */

    /** Slots on the home page's bố cáo strip. Matches PROMOTED_SLOT_COUNT. */
    public static final int BOARD_SLOTS = 12;

    /**
     * One slot on the board: the position, and what is sitting in it.
     * {@code promotionId} is null for an empty slot.
     */
    public record BoardSlot(
            int position, String promotionId, String storyId, String storyTitle,
            String storySlug, String coverUrl, String teamName,
            String startsAt, String endsAt, int durationDays) {
    }

    public record PromotionBoard(List<BoardSlot> slots, List<PromotionBooking> pending) {
    }

    /**
     * The board as the admin screen draws it: twelve slots, always twelve, plus
     * the queue waiting for a decision.
     *
     * <p>Empty slots are returned as rows rather than left out, so the screen
     * renders a fixed grid that mirrors the home page instead of a list that
     * changes shape.
     */
    public PromotionBoard board() {
        Map<Integer, BoardSlot> occupied = new java.util.HashMap<>();
        jdbc.sql("""
                        SELECT p.id, p.slot_position, p.duration_days, p.starts_at, p.ends_at,
                               s.id AS story_id, s.title, s.slug, s.cover_url, t.name AS team_name
                        FROM story_promotions p
                        JOIN stories s ON s.id = p.story_id
                        JOIN teams t ON t.id = s.team_id
                        WHERE p.status = 'ACTIVE' AND p.ends_at > NOW(3)
                        ORDER BY p.slot_position IS NULL, p.slot_position, p.created_at
                        """)
                .query((rs, rowNum) -> {
                    int slot = rs.getObject("slot_position") == null
                            ? rowNum + 1 : rs.getInt("slot_position");
                    return new BoardSlot(
                            slot,
                            rs.getString("id"),
                            rs.getString("story_id"),
                            rs.getString("title"),
                            rs.getString("slug"),
                            rs.getString("cover_url"),
                            rs.getString("team_name"),
                            instantOrNull(rs.getTimestamp("starts_at")),
                            instantOrNull(rs.getTimestamp("ends_at")),
                            rs.getInt("duration_days"));
                })
                .list()
                .forEach(row -> occupied.putIfAbsent(row.position(), row));

        List<BoardSlot> slots = new java.util.ArrayList<>(BOARD_SLOTS);
        for (int position = 1; position <= BOARD_SLOTS; position++) {
            slots.add(occupied.getOrDefault(position,
                    new BoardSlot(position, null, null, null, null, null, null, null, null, 0)));
        }
        return new PromotionBoard(slots, reviewQueue("PENDING"));
    }

    private static String instantOrNull(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    /** The lowest slot number nothing live is occupying, or null when full. */
    private Integer firstFreeSlot() {
        Set<Integer> taken = new java.util.HashSet<>(jdbc.sql("""
                        SELECT slot_position FROM story_promotions
                        WHERE status = 'ACTIVE' AND ends_at > NOW(3) AND slot_position IS NOT NULL
                        """)
                .query(Integer.class)
                .list());
        long live = jdbc.sql("""
                        SELECT COUNT(*) FROM story_promotions
                        WHERE status = 'ACTIVE' AND ends_at > NOW(3)
                        """)
                .query(Long.class).optional().orElse(0L);
        if (live >= BOARD_SLOTS) {
            return null;
        }
        for (int position = 1; position <= BOARD_SLOTS; position++) {
            if (!taken.contains(position)) {
                return position;
            }
        }
        return null;
    }

    /**
     * Writes the order the admin dragged the cards into.
     *
     * <p>Takes the whole arrangement rather than one move: positions have to
     * stay unique, and applying a single swap on its own would leave two cards
     * claiming a slot for as long as the second update took.
     */
    @Transactional
    public PromotionBoard reorder(Map<Integer, String> slotToPromotion) {
        // Cleared first so a card moving from 3 to 7 does not collide with
        // whatever is still recorded at 7 halfway through.
        jdbc.sql("""
                        UPDATE story_promotions SET slot_position = NULL
                        WHERE status = 'ACTIVE' AND ends_at > NOW(3)
                        """).update();
        slotToPromotion.forEach((position, promotionId) -> {
            if (promotionId == null || promotionId.isBlank()
                    || position == null || position < 1 || position > BOARD_SLOTS) {
                return;
            }
            jdbc.sql("UPDATE story_promotions SET slot_position = ?, updated_at = NOW(3) WHERE id = ?")
                    .params(position, promotionId)
                    .update();
        });
        return board();
    }

    /**
     * A bố cáo the admin places themselves, with no charge to the team.
     *
     * <p>For running the strip when nobody has booked - house picks, make-goods,
     * a slot promised offline. It goes straight to ACTIVE because there is
     * nobody to review it: the admin creating it is the review. `coin_paid` is
     * zero, so it never looks like revenue the team was billed for.
     */
    @Transactional
    public PromotionBooking adminCreate(UUID storyId, UUID packageId, String adminId) {
        StoryRow story = requireStory(storyId);
        PackageRow pkg = requirePackage(packageId);
        Integer slot = firstFreeSlot();
        if (slot == null) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.board_full",
                    "Kho bố cáo đã đầy",
                    "Cả %d vị trí đang có truyện. Hãy gỡ bớt một vị trí trước."
                            .formatted(BOARD_SLOTS));
        }

        Instant now = Instant.now();
        Instant endsAt = now.plus(Duration.ofDays(pkg.durationDays()));
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO story_promotions
                            (id, story_id, team_id, purchased_by, package_id, duration_days, coin_paid,
                             discount_coin, starts_at, ends_at, status, slot_position,
                             review_note, reviewed_by, reviewed_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?)
                        """)
                .params(id.toString(), storyId.toString(), story.teamId(), adminId,
                        pkg.id(), pkg.durationDays(),
                        Timestamp.from(now), Timestamp.from(endsAt), slot,
                        "Bố cáo do quản trị viên xếp, không tính phí.", adminId,
                        Timestamp.from(now), Timestamp.from(now), Timestamp.from(now))
                .update();
        return findBooking(id);
    }

    /** Frees a slot without touching the coins the team already paid. */
    @Transactional
    public PromotionBoard clearSlot(UUID promotionId) {
        jdbc.sql("""
                        UPDATE story_promotions
                        SET status = 'CANCELLED', slot_position = NULL,
                            cancelled_at = NOW(3), updated_at = NOW(3)
                        WHERE id = ?
                        """)
                .param(promotionId.toString())
                .update();
        return board();
    }

    /**
     * Accepts a booking but puts it on a date instead of a slot.
     *
     * <p>What the admin reaches for when the board is full: the buyer is told
     * their booking succeeded and when it will run, rather than being rejected
     * for a queue problem that is not their fault. The coins stay paid and the
     * request stays PENDING, so it is still in the queue to approve on the day.
     */
    @Transactional
    public PromotionBooking scheduleLater(UUID promotionId, String reviewerId, String scheduledLabel) {
        BookingRow booking = requireBooking(promotionId);
        if (!"PENDING".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.not_pending",
                    "Lượt bố cáo không còn chờ duyệt",
                    "Yêu cầu này đã được xử lý (%s).".formatted(booking.status()));
        }
        String label = scheduledLabel == null ? "" : scheduledLabel.trim();
        if (label.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "promotion.schedule_required",
                    "Thiếu lịch bố cáo", "Hãy nhập thời điểm dự kiến theo dạng hh:dd/mm/yyyy.");
        }

        jdbc.sql("""
                        UPDATE story_promotions
                        SET review_note = ?, reviewed_by = ?, reviewed_at = NOW(3), updated_at = NOW(3)
                        WHERE id = ?
                        """)
                .params("Đã xếp lịch: " + label, reviewerId, promotionId.toString())
                .update();

        notifyBuyer(booking, "Đăng ký bố cáo thành công",
                "Bạn đã đăng ký bố cáo thành công. Truyện sẽ được bố cáo vào %s.".formatted(label));
        return findBooking(promotionId);
    }

    /* --------------------------------------------------------- admin pricing */

    public record AdminPackageRow(
            String id, String code, String name, int durationDays,
            long priceCoin, String description, boolean active) {
    }

    public record TeamDiscountRow(String teamId, String slug, String name, long discountCoin) {
    }

    /** Includes retired packages, which an admin may want to bring back. */
    public List<AdminPackageRow> adminPackages() {
        return jdbc.sql("""
                        SELECT id, code, name, duration_days, price_coin, description, is_active
                        FROM promotion_packages
                        ORDER BY is_active DESC, sort_order, duration_days
                        """)
                .query((rs, rowNum) -> new AdminPackageRow(
                        rs.getString("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("duration_days"),
                        rs.getLong("price_coin"),
                        rs.getString("description"),
                        rs.getBoolean("is_active")))
                .list();
    }

    @Transactional
    public AdminPackageRow updatePackage(UUID packageId, Long priceCoin, Boolean active) {
        if (priceCoin != null && priceCoin <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "promotion.price_invalid",
                    "Giá gói không hợp lệ", "Giá gói bố cáo phải lớn hơn 0 xu.");
        }
        int changed = jdbc.sql("""
                        UPDATE promotion_packages
                        SET price_coin = COALESCE(?, price_coin),
                            is_active = COALESCE(?, is_active),
                            updated_at = NOW()
                        WHERE id = ?
                        """)
                .params(priceCoin, active, packageId.toString())
                .update();
        if (changed == 0) {
            throw notFound("promotion.package_not_found", "Không tìm thấy gói bố cáo");
        }
        return adminPackages().stream()
                .filter(row -> row.id().equals(packageId.toString()))
                .findFirst()
                .orElseThrow(() -> notFound("promotion.package_not_found", "Không tìm thấy gói bố cáo"));
    }

    /** Every team with its standing discount, so an admin can see them side by side. */
    public List<TeamDiscountRow> teamDiscounts() {
        return jdbc.sql("""
                        SELECT id, slug, name, promotion_discount_coin
                        FROM teams
                        ORDER BY promotion_discount_coin DESC, name
                        """)
                .query((rs, rowNum) -> new TeamDiscountRow(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getLong("promotion_discount_coin")))
                .list();
    }

    @Transactional
    public TeamDiscountRow updateTeamDiscount(UUID teamId, Long discountCoin) {
        long value = discountCoin == null ? 0L : discountCoin;
        if (value < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "promotion.discount_invalid",
                    "Mức giảm không hợp lệ", "Mức giảm không được âm.");
        }
        int changed = jdbc.sql("UPDATE teams SET promotion_discount_coin = ? WHERE id = ?")
                .params(value, teamId.toString())
                .update();
        if (changed == 0) {
            throw notFound("team.not_found", "Không tìm thấy nhóm");
        }
        return teamDiscounts().stream()
                .filter(row -> row.teamId().equals(teamId.toString()))
                .findFirst()
                .orElseThrow(() -> notFound("team.not_found", "Không tìm thấy nhóm"));
    }

    /**
     * The standing discount for a team, in xu. Zero unless an admin set one.
     *
     * <p>Deliberately has no reader- or publisher-facing endpoint. The team sees
     * the price it pays and nothing about how that price was reached, because
     * the arrangement is between the operator and that one partner.
     */
    private long teamDiscount(String teamId) {
        return jdbc.sql("SELECT promotion_discount_coin FROM teams WHERE id = ?")
                .param(teamId)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    @Transactional
    public PromotionBooking create(UUID userId, CreatePromotionRequest request) {
        UUID storyId = request.storyId();
        StoryRow story = requireStory(storyId);
        requireCanPromote(userId, story);

        PackageRow pkg = requirePackage(request.packageId());
        Instant now = Instant.now();
        Instant startsAt = now;

        // Stacking onto a live booking keeps the slot continuous instead of
        // restarting the window and losing paid-for days.
        Optional<Instant> currentEnd = activeEndsAt(storyId);
        if (currentEnd.isPresent()) {
            startsAt = currentEnd.get();
        }
        Instant endsAt = startsAt.plus(Duration.ofDays(pkg.durationDays()));
        requireWithinCap(now, endsAt);

        // The team's standing discount, taken straight off the list price. It is
        // never sent back to the team - only the figure they actually pay is.
        long discount = teamDiscount(story.teamId());
        long charged = Math.max(0, pkg.priceCoin() - discount);
        // What was really taken off, which is less than the discount when the
        // discount is larger than the package. Storing the applied amount keeps
        // `coin_paid + discount_coin` equal to the list price on every row.
        long applied = pkg.priceCoin() - charged;

        // Coins are taken now, so a request cannot be placed without the means
        // to pay for it. A rejection refunds them in full.
        if (charged > 0) {
            chargeWallet(userId, charged, storyId, story.title());
        }

        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO story_promotions
                            (id, story_id, team_id, purchased_by, package_id, duration_days, coin_paid,
                             discount_coin, starts_at, ends_at, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                        """)
                .params(id.toString(), storyId.toString(), story.teamId(), userId.toString(),
                        pkg.id(), pkg.durationDays(), charged, applied,
                        Timestamp.from(startsAt), Timestamp.from(endsAt),
                        Timestamp.from(now), Timestamp.from(now))
                .update();

        return findBooking(id);
    }

    /**
     * Approves a booking, which is when its days actually start counting.
     *
     * <p>The window is recalculated from the approval rather than the request:
     * a booking sitting in the queue overnight would otherwise burn a day of
     * the slot the buyer paid for.
     */
    @Transactional
    public PromotionBooking approve(UUID promotionId, String reviewerId, String note) {
        BookingRow booking = requireBooking(promotionId);
        if (!"PENDING".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.not_pending",
                    "Lượt bố cáo không còn chờ duyệt",
                    "Yêu cầu này đã được xử lý (%s).".formatted(booking.status()));
        }

        Instant now = Instant.now();
        int durationDays = jdbc.sql("SELECT duration_days FROM story_promotions WHERE id = ?")
                .param(promotionId.toString())
                .query(Integer.class)
                .single();

        // Queue behind whatever is already running for this story.
        Instant startsAt = activeEndsAt(UUID.fromString(booking.storyId()))
                .filter(end -> end.isAfter(now))
                .orElse(now);
        // Duration.ofDays is exact hours, so a 3-day booking runs a full 72
        // hours from the moment it is approved - not until the end of the third
        // calendar day.
        Instant endsAt = startsAt.plus(Duration.ofDays(durationDays));

        // The board holds twelve. Approving a thirteenth would put a booking on
        // the home page that no slot can show, so it is refused here with a code
        // the admin screen turns into the "board is full" form rather than a
        // generic failure.
        Integer slot = firstFreeSlot();
        if (slot == null) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.board_full",
                    "Kho bố cáo đã đầy",
                    ("Cả %d vị trí bố cáo đang có truyện. Hãy gỡ bớt một vị trí, "
                            + "hoặc hẹn lịch cho yêu cầu này.").formatted(BOARD_SLOTS));
        }

        jdbc.sql("""
                        UPDATE story_promotions
                        SET status = 'ACTIVE', starts_at = ?, ends_at = ?, slot_position = ?,
                            review_note = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?
                        WHERE id = ?
                        """)
                .params(Timestamp.from(startsAt), Timestamp.from(endsAt), slot, note, reviewerId,
                        Timestamp.from(now), Timestamp.from(now), promotionId.toString())
                .update();

        notifyBuyer(booking, "Bố cáo đã được duyệt",
                "Truyện của bạn sẽ hiển thị tại khu Bố cáo trang chủ trong %d ngày.%s"
                        .formatted(durationDays, note == null || note.isBlank() ? "" : " Ghi chú: " + note));

        return findBooking(promotionId);
    }

    /** Rejects a booking, refunds the coins, and tells the buyer why. */
    @Transactional
    public PromotionBooking reject(UUID promotionId, String reviewerId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "promotion.reason_required",
                    "Thiếu lý do từ chối",
                    "Hãy nhập lý do từ chối; người đăng ký sẽ nhận được nội dung này.");
        }
        BookingRow booking = requireBooking(promotionId);
        if (!"PENDING".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.not_pending",
                    "Lượt bố cáo không còn chờ duyệt",
                    "Yêu cầu này đã được xử lý (%s).".formatted(booking.status()));
        }

        Instant now = Instant.now();
        long coinPaid = jdbc.sql("SELECT coin_paid FROM story_promotions WHERE id = ?")
                .param(promotionId.toString())
                .query(Long.class)
                .single();

        jdbc.sql("""
                        UPDATE story_promotions
                        SET status = 'REJECTED', review_note = ?, reviewed_by = ?,
                            reviewed_at = ?, updated_at = ?
                        WHERE id = ?
                        """)
                .params(reason.trim(), reviewerId, Timestamp.from(now), Timestamp.from(now),
                        promotionId.toString())
                .update();

        refundWallet(booking.purchasedBy(), coinPaid, promotionId);

        notifyBuyer(booking, "Bố cáo bị từ chối",
                "Yêu cầu bố cáo đã bị từ chối và %d xu đã được hoàn lại ví của bạn. Lý do: %s"
                        .formatted(coinPaid, reason.trim()));

        return findBooking(promotionId);
    }

    /** Returns the coins a rejected booking took, and records the movement. */
    private void refundWallet(String userId, long coin, UUID promotionId) {
        if (coin <= 0) {
            return;
        }
        jdbc.sql("UPDATE wallets SET coin_balance = coin_balance + ?, updated_at = NOW(3) WHERE user_id = ?")
                .params(coin, userId)
                .update();

        long balance = jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ?")
                .param(userId)
                .query(Long.class)
                .single();

        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, 'COIN', 'REFUND', ?, ?, ?, ?, ?, NOW(3))
                        """)
                .params(UUID.randomUUID().toString(), userId, coin, balance,
                        REFERENCE_TYPE, promotionId.toString(), "Hoàn xu bố cáo bị từ chối")
                .update();
    }

    private void notifyBuyer(BookingRow booking, String title, String message) {
        jdbc.sql("""
                        INSERT INTO notifications
                            (id, user_id, type, title, message, target_type, target_id, target_url, created_at)
                        VALUES (?, ?, 'SYSTEM', ?, ?, 'PROMOTION', ?, '/promotions', NOW(3))
                        """)
                .params(UUID.randomUUID().toString(), booking.purchasedBy(), title, message, booking.id())
                .update();
    }

    private BookingRow requireBooking(UUID promotionId) {
        return jdbc.sql("""
                        SELECT id, story_id, team_id, purchased_by, ends_at, status
                        FROM story_promotions WHERE id = ?
                        """)
                .param(promotionId.toString())
                .query((rs, rowNum) -> new BookingRow(
                        rs.getString("id"),
                        rs.getString("story_id"),
                        rs.getString("team_id"),
                        rs.getString("purchased_by"),
                        rs.getTimestamp("ends_at").toInstant(),
                        rs.getString("status")))
                .optional()
                .orElseThrow(() -> notFound("promotion.not_found", "Không tìm thấy lượt bố cáo"));
    }

    @Transactional
    public PromotionBooking extend(UUID userId, UUID promotionId, ExtendPromotionRequest request) {
        BookingRow booking = requireBooking(promotionId);

        if (!"ACTIVE".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.not_active",
                    "Lượt bố cáo không còn hiệu lực",
                    "Chỉ có thể gia hạn lượt bố cáo đang chạy");
        }

        StoryRow story = requireStory(UUID.fromString(booking.storyId()));
        requireCanPromote(userId, story);

        PackageRow pkg = requirePackage(request.packageId());
        Instant now = Instant.now();
        // An expired-but-not-yet-swept booking restarts from now, not from the past.
        Instant startsAt = booking.endsAt().isAfter(now) ? booking.endsAt() : now;
        Instant endsAt = startsAt.plus(Duration.ofDays(pkg.durationDays()));
        requireWithinCap(now, endsAt);

        // Extending is another purchase, so the team's discount applies again -
        // otherwise a partner would be charged list price for renewing.
        long discount = teamDiscount(story.teamId());
        long charged = Math.max(0, pkg.priceCoin() - discount);
        long applied = pkg.priceCoin() - charged;

        if (charged > 0) {
            chargeWallet(userId, charged, UUID.fromString(booking.storyId()), story.title());
        }

        // A renewal is a new request, not an edit to the live one.
        //
        // Adding the days straight onto the running booking put paid-for time on
        // the home page without anyone approving it - a story could be rejected
        // on its first request and then extend its way back on. The new stretch
        // queues as PENDING and starts where the current one ends, so nothing is
        // lost while it waits.
        UUID renewalId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO story_promotions
                            (id, story_id, team_id, purchased_by, package_id, duration_days, coin_paid,
                             discount_coin, starts_at, ends_at, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                        """)
                .params(renewalId.toString(), booking.storyId(), story.teamId(), userId.toString(),
                        pkg.id(), pkg.durationDays(), charged, applied,
                        Timestamp.from(startsAt), Timestamp.from(endsAt),
                        Timestamp.from(now), Timestamp.from(now))
                .update();

        return findBooking(renewalId);
    }

    /** Flips finished bookings to EXPIRED so the home page stops showing them. */
    @Transactional
    public int sweepExpired() {
        return jdbc.sql("""
                        UPDATE story_promotions
                        SET status = 'EXPIRED', updated_at = NOW(3)
                        WHERE status = 'ACTIVE' AND ends_at <= NOW(3)
                        """)
                .update();
    }

    // -----------------------------------------------------------------
    // Guards
    // -----------------------------------------------------------------

    /**
     * Only an active member of the owning team may spend coins on that team's
     * story; admins are allowed through for operational fixes.
     */
    private void requireCanPromote(UUID userId, StoryRow story) {
        if (!"PUBLISHED".equals(story.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.story_not_published",
                    "Truyện chưa xuất bản",
                    "Chỉ truyện đã xuất bản mới được đăng ký bố cáo");
        }
        if (isAdmin(userId)) {
            return;
        }
        long member = jdbc.sql("""
                        SELECT COUNT(*) FROM team_members
                        WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'
                        """)
                .params(story.teamId(), userId.toString())
                .query(Long.class).optional().orElse(0L);
        if (member == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "promotion.not_team_member",
                    "Không có quyền đăng ký bố cáo",
                    "Bạn phải là thành viên đang hoạt động của nhóm sở hữu truyện này");
        }
    }

    private void requireWithinCap(Instant now, Instant endsAt) {
        Instant cap = now.plus(Duration.ofDays(MAX_TOTAL_DAYS));
        if (endsAt.isAfter(cap)) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.exceeds_max_duration",
                    "Vượt quá thời hạn tối đa",
                    "Tổng thời gian bố cáo không được vượt quá " + MAX_TOTAL_DAYS
                            + " ngày kể từ hôm nay. Hãy gia hạn khi lượt hiện tại sắp hết.");
        }
    }

    /**
     * Debits the buyer's wallet under a row lock and appends the ledger entry in
     * the same transaction, so a concurrent purchase cannot overdraw.
     */
    private void chargeWallet(UUID userId, long priceCoin, UUID storyId, String storyTitle) {
        Long balance = jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ? FOR UPDATE")
                .param(userId.toString())
                .query(Long.class)
                .optional()
                .orElseThrow(() -> notFound("wallet.not_found", "Không tìm thấy ví của bạn"));

        if (balance < priceCoin) {
            throw new ApiException(HttpStatus.CONFLICT, "promotion.insufficient_balance",
                    "Số dư không đủ",
                    "Cần " + priceCoin + " xu nhưng ví chỉ còn " + balance + " xu");
        }

        long balanceAfter = balance - priceCoin;
        Instant now = Instant.now();

        jdbc.sql("UPDATE wallets SET coin_balance = ?, updated_at = ? WHERE user_id = ?")
                .params(balanceAfter, Timestamp.from(now), userId.toString())
                .update();

        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, 'COIN', ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(UUID.randomUUID().toString(), userId.toString(), TRANSACTION_TYPE,
                        -priceCoin, balanceAfter, REFERENCE_TYPE, storyId.toString(),
                        "Đăng ký bố cáo: " + storyTitle, Timestamp.from(now))
                .update();
    }

    // -----------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------

    private boolean isAdmin(UUID userId) {
        return jdbc.sql("SELECT role FROM users WHERE id = ?")
                .param(userId.toString())
                .query(String.class)
                .optional()
                .filter("ADMIN"::equals)
                .isPresent();
    }

    private StoryRow requireStory(UUID storyId) {
        return jdbc.sql("SELECT id, title, team_id, status FROM stories WHERE id = ?")
                .param(storyId.toString())
                .query((rs, rowNum) -> new StoryRow(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("team_id"),
                        rs.getString("status")))
                .optional()
                .orElseThrow(() -> notFound("promotion.story_not_found", "Không tìm thấy truyện"));
    }

    private PackageRow requirePackage(UUID packageId) {
        return jdbc.sql("""
                        SELECT id, duration_days, price_coin
                        FROM promotion_packages WHERE id = ? AND is_active = TRUE
                        """)
                .param(packageId.toString())
                .query((rs, rowNum) -> new PackageRow(
                        rs.getString("id"),
                        rs.getInt("duration_days"),
                        rs.getLong("price_coin")))
                .optional()
                .orElseThrow(() -> notFound("promotion.package_not_found",
                        "Không tìm thấy gói bố cáo đang mở bán"));
    }

    private Optional<Instant> activeEndsAt(UUID storyId) {
        return jdbc.sql("""
                        SELECT MAX(ends_at) FROM story_promotions
                        WHERE story_id = ? AND status = 'ACTIVE' AND ends_at > NOW(3)
                        """)
                .param(storyId.toString())
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    private long walletBalance(UUID userId) {
        return jdbc.sql("SELECT COALESCE(coin_balance, 0) FROM wallets WHERE user_id = ?")
                .param(userId.toString())
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    private PromotionBooking findBooking(UUID id) {
        return jdbc.sql(BOOKING_SELECT + " WHERE p.id = ?")
                .param(id.toString())
                .query((rs, rowNum) -> toBooking(rs))
                .single();
    }

    /** The admin review queue: pending first, then the most recent decisions. */
    @Transactional(readOnly = true)
    public List<PromotionBooking> reviewQueue(String status) {
        String filter = status == null || status.isBlank() ? null
                : status.trim().toUpperCase(java.util.Locale.ROOT);
        return jdbc.sql(BOOKING_SELECT + """
                        WHERE (? IS NULL OR p.status = ?)
                        ORDER BY p.status = 'PENDING' DESC, p.created_at DESC
                        LIMIT 200
                        """)
                .params(filter, filter)
                .query((rs, rowNum) -> toBooking(rs))
                .list();
    }

    private static PromotableStory toPromotableStory(ResultSet rs) throws SQLException {
        Timestamp activeUntil = rs.getTimestamp("active_until");
        Instant until = activeUntil == null ? null : activeUntil.toInstant();
        return new PromotableStory(
                rs.getString("id"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("team_id"),
                rs.getString("team_name"),
                until != null,
                until == null ? null : until.toString(),
                daysRemaining(until)
        );
    }

    private static PromotionBooking toBooking(ResultSet rs) throws SQLException {
        Instant endsAt = rs.getTimestamp("ends_at").toInstant();
        return new PromotionBooking(
                rs.getString("id"),
                rs.getString("story_id"),
                rs.getString("story_title"),
                rs.getString("story_slug"),
                rs.getString("team_id"),
                rs.getString("team_name"),
                rs.getInt("duration_days"),
                rs.getLong("coin_paid"),
                rs.getTimestamp("starts_at").toInstant().toString(),
                endsAt.toString(),
                rs.getString("status"),
                "ACTIVE".equals(rs.getString("status")) ? daysRemaining(endsAt) : 0,
                rs.getString("review_note"),
                rs.getTimestamp("reviewed_at") == null
                        ? null : rs.getTimestamp("reviewed_at").toInstant().toString(),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getString("purchased_by_email")
        );
    }

    /** Rounds up so a booking with any time left still reads as one day. */
    private static int daysRemaining(Instant until) {
        if (until == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(), until).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (int) Math.ceil(seconds / 86400.0);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message, message);
    }

    private record StoryRow(String id, String title, String teamId, String status) {}

    private record PackageRow(String id, int durationDays, long priceCoin) {}

    private record BookingRow(
            String id, String storyId, String teamId, String purchasedBy, Instant endsAt, String status) {}
}
