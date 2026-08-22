package com.storyplatform.unit.monetization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyplatform.monetization.application.MonetizationFlowService;
import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Vòng đời một yêu cầu rút tiền, chạy trên đúng lược đồ thật.
 *
 * <p>Xu bị trừ khỏi ví ngay lúc người dùng gửi yêu cầu. Vì vậy điều phải chứng
 * minh ở đây không phải là "trạng thái có đổi không" mà là "số dư có về đúng
 * chỗ không": một yêu cầu bị huỷ mà không hoàn xu là người dùng mất trắng, và
 * một yêu cầu bị huỷ hai lần mà hoàn hai lần là nền tảng mất trắng.
 */
class WithdrawalFlowTest {

    private MonetizationFlowService service;
    private JdbcClient jdbc;

    private UUID owner;
    private UUID admin;
    private UUID team;

    /**
     * Một cơ sở dữ liệu cục bộ, hoặc bài kiểm tự bước sang bên.
     *
     * <p>Cố ý không dùng {@code @SpringBootTest}: cả gói {@code integration/**}
     * bị loại khỏi bước biên dịch trong pom, nên một bài kiểm dựng Boot context
     * đặt ở đây sẽ lặng lẽ không bao giờ chạy.
     */
    @BeforeEach
    void connect() {
        String url = System.getenv().getOrDefault("MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/gioitruyen?useUnicode=true&characterEncoding=utf8"
                        + "&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false");
        String user = System.getenv().getOrDefault("MYSQL_USER", "gioitruyen");
        String password = System.getenv().getOrDefault("MYSQL_PASSWORD", "");

        DriverManagerDataSource source = new DriverManagerDataSource(url, user, password);
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try (var probe = source.getConnection()) {
            Assumptions.assumeTrue(probe.isValid(2), "Không kết nối được MySQL cục bộ");
        } catch (Exception unavailable) {
            Assumptions.abort("Bỏ qua: không có MySQL cục bộ (" + unavailable.getMessage() + ")");
        }

        jdbc = JdbcClient.create((DataSource) source);
        service = new MonetizationFlowService(
                new NamedParameterJdbcTemplate(source), 100_000L, 1_000_000L, 20_000L, 1_000_000_000L);

        Assumptions.assumeTrue(
                jdbc.sql("""
                                SELECT COUNT(1) FROM information_schema.COLUMNS
                                WHERE TABLE_SCHEMA = DATABASE()
                                  AND TABLE_NAME = 'withdrawal_requests'
                                  AND COLUMN_NAME = 'refunded_at'
                                """)
                        .query(Long.class).single() > 0,
                "Bỏ qua: CSDL cục bộ chưa chạy migration V30");

        seed();
    }

    void seed() {
        owner = UUID.randomUUID();
        admin = UUID.randomUUID();
        team = UUID.randomUUID();

        insertUser(owner, "wd-owner");
        insertUser(admin, "wd-admin");

        jdbc.sql("""
                INSERT INTO teams (id, slug, name, description, status, created_by)
                VALUES (?, ?, 'Withdrawal Test Team', 'fixture', 'ACTIVE', ?)
                """)
                .params(team.toString(), "wd-test-" + team, owner.toString())
                .update();

        fund(owner, 500_000L);
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null || team == null) return;
        jdbc.sql("DELETE FROM withdrawal_requests WHERE team_id = ? OR user_id = ?")
                .params(team.toString(), owner.toString()).update();
        jdbc.sql("DELETE FROM teams WHERE id = ?").param(team.toString()).update();
        for (UUID user : List.of(owner, admin)) {
            jdbc.sql("DELETE FROM wallet_transactions WHERE user_id = ?").param(user.toString()).update();
            jdbc.sql("DELETE FROM wallets WHERE user_id = ?").param(user.toString()).update();
            jdbc.sql("DELETE FROM users WHERE id = ?").param(user.toString()).update();
        }
    }

    /* ── Đường đi thuận ─────────────────────────────────────────────── */

    @Test
    @DisplayName("gửi yêu cầu trừ xu ngay và mở ở trạng thái chờ duyệt")
    void reservesCoinsOnRequest() {
        var receipt = request(200_000L);

        assertThat(receipt.state()).isEqualTo("PENDING_REVIEW");
        assertThat(receipt.grossAmountXu()).isEqualTo(200_000L);
        assertThat(receipt.feeXu()).isEqualTo(20_000L);
        assertThat(receipt.netAmountXu()).isEqualTo(180_000L);
        assertThat(balance(owner)).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("duyệt rồi chuyển tiền rồi người rút xác nhận: khép trọn vòng")
    void walksTheHappyPath() {
        var receipt = request(200_000L);

        var approved = service.approveWithdrawal(admin, receipt.id(), "Sẽ chuyển trong hôm nay");
        assertThat(approved.state()).isEqualTo("APPROVED");
        assertThat(approved.adminNote()).isEqualTo("Sẽ chuyển trong hôm nay");
        assertThat(approved.reviewedAt()).isNotNull();

        var paid = service.markWithdrawalPaid(admin, receipt.id(), "FT26082212345678", null);
        assertThat(paid.state()).isEqualTo("PAID");
        assertThat(paid.transferReference()).isEqualTo("FT26082212345678");
        assertThat(paid.paidAt()).isNotNull();

        var done = service.confirmWithdrawal(owner, receipt.id());
        assertThat(done.state()).isEqualTo("COMPLETED");
        assertThat(done.confirmedAt()).isNotNull();
        // Tiền đã đi thật, nên xu không quay lại ví.
        assertThat(balance(owner)).isEqualTo(300_000L);
    }

    /** Chuyển thẳng từ chờ duyệt sang đã chuyển, bỏ qua bước duyệt riêng. */
    @Test
    @DisplayName("chuyển tiền được ngay từ trạng thái chờ duyệt")
    void allowsPayingWithoutASeparateApproval() {
        var receipt = request(200_000L);
        assertThat(service.markWithdrawalPaid(admin, receipt.id(), null, null).state()).isEqualTo("PAID");
    }

    /* ── Huỷ và hoàn xu ─────────────────────────────────────────────── */

    @Test
    @DisplayName("huỷ hoàn đủ xu về ví và ghi lại lý do")
    void refundsOnRejection() {
        var receipt = request(200_000L);
        assertThat(balance(owner)).isEqualTo(300_000L);

        var rejected = service.rejectWithdrawal(admin, receipt.id(), "Tên tài khoản không khớp");

        assertThat(rejected.state()).isEqualTo("REJECTED");
        assertThat(rejected.adminNote()).isEqualTo("Tên tài khoản không khớp");
        assertThat(balance(owner)).isEqualTo(500_000L);
    }

    /** Người rút phải đọc được vì sao tiền của mình quay về ví. */
    @Test
    @DisplayName("huỷ mà không nêu lý do thì bị từ chối")
    void refusesRejectionWithoutAReason() {
        var receipt = request(200_000L);

        assertThatThrownBy(() -> service.rejectWithdrawal(admin, receipt.id(), "   "))
                .isInstanceOf(ApiException.class);
        // Không được đổi trạng thái khi lệnh bị từ chối.
        assertThat(state(receipt.id())).isEqualTo("PENDING_REVIEW");
        assertThat(balance(owner)).isEqualTo(300_000L);
    }

    /**
     * Chốt chặn quan trọng nhất của cả tệp này: hoàn xu đúng một lần.
     *
     * <p>Hai quản trị viên cùng bấm huỷ, hoặc một người bấm hai lần vì trang
     * chưa kịp tải lại - nếu lần thứ hai cũng cộng xu thì nền tảng mất tiền.
     */
    @Test
    @DisplayName("huỷ lần thứ hai không cộng thêm xu lần nữa")
    void refundsOnlyOnce() {
        var receipt = request(200_000L);
        service.rejectWithdrawal(admin, receipt.id(), "Lý do lần một");

        assertThatThrownBy(() -> service.rejectWithdrawal(admin, receipt.id(), "Lý do lần hai"))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("withdrawal.wrong_state"));
        assertThat(balance(owner)).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("huỷ sau khi đã chuyển tiền vẫn hoàn xu")
    void refundsEvenAfterPaid() {
        var receipt = request(200_000L);
        service.markWithdrawalPaid(admin, receipt.id(), null, null);

        service.rejectWithdrawal(admin, receipt.id(), "Ngân hàng trả lại lệnh chuyển");

        assertThat(balance(owner)).isEqualTo(500_000L);
    }

    /** Yêu cầu đã khép lại thì không ai lật lại được, kể cả quản trị viên. */
    @Test
    @DisplayName("không huỷ được yêu cầu người rút đã xác nhận")
    void refusesRejectingACompletedRequest() {
        var receipt = request(200_000L);
        service.markWithdrawalPaid(admin, receipt.id(), null, null);
        service.confirmWithdrawal(owner, receipt.id());

        assertThatThrownBy(() -> service.rejectWithdrawal(admin, receipt.id(), "Đổi ý"))
                .isInstanceOf(ApiException.class);
        assertThat(balance(owner)).isEqualTo(300_000L);
    }

    /* ── Phản hồi của người rút ─────────────────────────────────────── */

    @Test
    @DisplayName("báo chưa nhận đưa yêu cầu về lại bàn quản trị viên")
    void sendsADisputeBack() {
        var receipt = request(200_000L);
        service.markWithdrawalPaid(admin, receipt.id(), "FT1", null);

        var disputed = service.disputeWithdrawal(owner, receipt.id(), "Sao kê tới 22/8 chưa thấy khoản nào");

        assertThat(disputed.state()).isEqualTo("DISPUTED");
        assertThat(disputed.confirmNote()).isEqualTo("Sao kê tới 22/8 chưa thấy khoản nào");
    }

    @Test
    @DisplayName("chuyển lại được sau khi người rút báo chưa nhận")
    void allowsPayingAgainAfterADispute() {
        var receipt = request(200_000L);
        service.markWithdrawalPaid(admin, receipt.id(), "FT1", null);
        service.disputeWithdrawal(owner, receipt.id(), "Chưa thấy tiền");

        var again = service.markWithdrawalPaid(admin, receipt.id(), "FT2", "Đã chuyển lại");

        assertThat(again.state()).isEqualTo("PAID");
        assertThat(again.transferReference()).isEqualTo("FT2");
        // Lời báo cũ được xoá: nó nói về lần chuyển trước, giữ lại chỉ gây nhầm.
        assertThat(again.confirmNote()).isNull();
    }

    @Test
    @DisplayName("báo chưa nhận mà không ghi chú thì bị từ chối")
    void refusesADisputeWithoutANote() {
        var receipt = request(200_000L);
        service.markWithdrawalPaid(admin, receipt.id(), null, null);

        assertThatThrownBy(() -> service.disputeWithdrawal(owner, receipt.id(), ""))
                .isInstanceOf(ApiException.class);
        assertThat(state(receipt.id())).isEqualTo("PAID");
    }

    @Test
    @DisplayName("chưa chuyển tiền thì chưa xác nhận được")
    void refusesConfirmingBeforePayment() {
        var receipt = request(200_000L);

        assertThatThrownBy(() -> service.confirmWithdrawal(owner, receipt.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("withdrawal.wrong_state"));
    }

    /** Không ai được xác nhận hộ yêu cầu rút tiền của người khác. */
    @Test
    @DisplayName("người khác không xác nhận hộ được")
    void refusesConfirmingSomebodyElsesRequest() {
        var receipt = request(200_000L);
        service.markWithdrawalPaid(admin, receipt.id(), null, null);

        assertThatThrownBy(() -> service.confirmWithdrawal(admin, receipt.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("withdrawal.not_found"));
        assertThat(state(receipt.id())).isEqualTo("PAID");
    }

    /* ── Hàng đợi của quản trị viên ─────────────────────────────────── */

    @Test
    @DisplayName("hàng đợi lọc theo trạng thái và mang đủ số tài khoản")
    void listsTheQueue() {
        var receipt = request(200_000L);

        var pending = service.adminWithdrawals("PENDING_REVIEW").stream()
                .filter(row -> row.id().equals(receipt.id()))
                .findFirst();

        assertThat(pending).isPresent();
        // Quản trị viên phải gõ số này vào giao diện ngân hàng, che đi thì
        // không chuyển tiền được.
        assertThat(pending.get().accountNumber()).isEqualTo("0123456789");
        assertThat(pending.get().userEmail()).contains("wd-owner");

        service.rejectWithdrawal(admin, receipt.id(), "Không hợp lệ");
        assertThat(service.adminWithdrawals("PENDING_REVIEW").stream()
                .anyMatch(row -> row.id().equals(receipt.id()))).isFalse();
        assertThat(service.adminWithdrawals("REJECTED").stream()
                .anyMatch(row -> row.id().equals(receipt.id()))).isTrue();
    }

    /* ── Trợ thủ ────────────────────────────────────────────────────── */

    private MonetizationFlowService.WithdrawalReceipt request(long gross) {
        return service.createWithdrawal(owner, false, new MonetizationFlowService.WithdrawalRequest(
                "NGUYEN VAN A", "0123456789", "Vietcombank", gross));
    }

    private String state(UUID id) {
        return jdbc.sql("SELECT state FROM withdrawal_requests WHERE id = ?")
                .param(id.toString()).query(String.class).single();
    }

    private long balance(UUID user) {
        return jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ?")
                .param(user.toString()).query(Long.class).single();
    }

    private void insertUser(UUID id, String prefix) {
        jdbc.sql("""
                INSERT INTO users (id, email, username, display_name, role, status)
                VALUES (?, ?, ?, ?, 'READER', 'ACTIVE')
                """)
                .params(id.toString(), prefix + "-" + id + "@test.local", prefix + "-" + id, prefix)
                .update();
        jdbc.sql("INSERT INTO wallets (id, user_id, coin_balance) VALUES (?, ?, 0)")
                .params(UUID.randomUUID().toString(), id.toString())
                .update();
    }

    private void fund(UUID user, long amount) {
        jdbc.sql("UPDATE wallets SET coin_balance = ? WHERE user_id = ?")
                .params(amount, user.toString())
                .update();
    }
}
