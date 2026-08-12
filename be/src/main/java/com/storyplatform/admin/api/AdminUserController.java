package com.storyplatform.admin.api;

import static com.storyplatform.admin.application.dto.AdminDtos.timestamp;

import com.storyplatform.admin.application.dto.AdminDtos.AdminUserRow;
import com.storyplatform.admin.application.dto.AdminDtos.UpsertUserRequest;
import com.storyplatform.auth.domain.User;
import com.storyplatform.auth.domain.UserRole;
import com.storyplatform.auth.domain.UserStatus;
import com.storyplatform.auth.infrastructure.UserRepository;
import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/content/users")
public class AdminUserController {

    private static final String LIST_SQL = """
            SELECT u.id, u.email, u.display_name, u.status, u.role, u.created_at, u.updated_at,
                   p.bio,
                   COALESCE(w.coin_balance, 0) AS coin_balance
            FROM users u
            LEFT JOIN user_profiles p ON p.user_id = u.id
            LEFT JOIN wallets w ON w.user_id = u.id
            ORDER BY u.created_at DESC
            """;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcClient jdbc;

    public AdminUserController(UserRepository userRepository, PasswordEncoder passwordEncoder, JdbcClient jdbc) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminUserRow> list() {
        return jdbc.sql(LIST_SQL)
                .query((rs, rowNum) -> new AdminUserRow(
                        rs.getString("id"),
                        rs.getString("email"),
                        AdminCategoryController.nullToEmpty(rs.getString("display_name")),
                        AdminCategoryController.nullToEmpty(rs.getString("bio")),
                        rs.getString("status"),
                        rs.getString("role"),
                        rs.getLong("coin_balance"),
                        timestamp(rs, "created_at"),
                        timestamp(rs, "updated_at")
                ))
                .list();
    }

    @PostMapping
    @Transactional
    public AdminUserRow create(@RequestBody UpsertUserRequest request) {
        AdminCategoryController.requireText(request.email(), "email");
        AdminCategoryController.requireText(request.password(), "password");
        requireEmailAvailable(request.email(), null);

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        UserRole role = resolveRole(request.roles());
        UserStatus status = AdminStoryController.parseEnum(UserStatus.class, request.state(), UserStatus.ACTIVE);

        // Ids are assigned here, so an explicit INSERT is used; repository.save()
        // would treat the populated id as an existing row and emit an UPDATE.
        jdbc.sql("""
                        INSERT INTO users (id, email, username, password_hash, display_name,
                                           role, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id.toString(),
                        request.email().trim().toLowerCase(java.util.Locale.ROOT),
                        uniqueUsername(request.email()),
                        passwordEncoder.encode(request.password()),
                        displayNameOrEmailPrefix(request.displayName(), request.email()),
                        role.name(), status.name(),
                        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                .update();

        upsertProfile(id, request.bio());
        ensureWallet(id);
        return toRow(find(id), request.bio());
    }

    @PutMapping("/{id}")
    @Transactional
    public AdminUserRow update(@PathVariable UUID id, @RequestBody UpsertUserRequest request) {
        User user = find(id);
        if (request.email() != null && !request.email().isBlank()) {
            requireEmailAvailable(request.email(), id);
            user.setEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.roles() != null && !request.roles().isEmpty()) {
            user.setRole(resolveRole(request.roles()));
        }
        if (request.state() != null && !request.state().isBlank()) {
            user.setStatus(AdminStoryController.parseEnum(UserStatus.class, request.state(), UserStatus.ACTIVE));
        }
        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        upsertProfile(saved.getId(), request.bio());
        return toRow(saved, request.bio());
    }

    /**
     * Users are referenced by stories, teams and ledger rows with ON DELETE
     * RESTRICT, so archiving bans the account instead of deleting it.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void archive(@PathVariable UUID id) {
        User user = find(id);
        user.setStatus(UserStatus.BANNED);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    /**
     * Removes an account outright.
     *
     * <p>Refused as soon as money or content is attached: payments, purchases
     * and owned teams are all ON DELETE RESTRICT, and an account that spent
     * coins is part of the ledger. Banning is the action for everything else.
     */
    @DeleteMapping("/{id}/permanent")
    @Transactional
    public void deletePermanently(@PathVariable UUID id) {
        User user = find(id);
        long attachments = jdbc.sql("""
                        SELECT (SELECT COUNT(*) FROM payments WHERE user_id = :userId)
                             + (SELECT COUNT(*) FROM purchase_orders WHERE user_id = :userId)
                             + (SELECT COUNT(*) FROM wallet_transactions WHERE user_id = :userId)
                             + (SELECT COUNT(*) FROM teams WHERE created_by = :userId)
                        """)
                .param("userId", id.toString())
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (attachments > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "user.has_history",
                    "Account has financial history",
                    ("Tài khoản \"%s\" đã có %d bản ghi giao dịch hoặc team nên không thể xóa. "
                            + "Hãy dùng \"Tạm khóa\" để vô hiệu hóa tài khoản.")
                            .formatted(user.getEmail(), attachments));
        }
        jdbc.sql("DELETE FROM users WHERE id = ?").param(id.toString()).update();
    }

    private void upsertProfile(UUID userId, String bio) {
        if (bio == null) {
            return;
        }
        jdbc.sql("INSERT INTO user_profiles (user_id, bio, updated_at) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE bio = VALUES(bio), updated_at = VALUES(updated_at)")
                .params(userId.toString(), bio, java.sql.Timestamp.from(Instant.now()))
                .update();
    }

    private void ensureWallet(UUID userId) {
        jdbc.sql("INSERT IGNORE INTO wallets (id, user_id, coin_balance, gem_balance, updated_at) "
                        + "VALUES (?, ?, 0, 0, ?)")
                .params(UUID.randomUUID().toString(), userId.toString(), java.sql.Timestamp.from(Instant.now()))
                .update();
    }

    private void requireEmailAvailable(String email, UUID excludedId) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        long taken = excludedId == null
                ? jdbc.sql("SELECT COUNT(*) FROM users WHERE email = ?")
                        .param(normalized).query(Long.class).optional().orElse(0L)
                : jdbc.sql("SELECT COUNT(*) FROM users WHERE email = ? AND id <> ?")
                        .params(normalized, excludedId.toString()).query(Long.class).optional().orElse(0L);
        if (taken > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "user.email_taken",
                    "Email already used", "Email already used by another account");
        }
    }

    /** users.username is UNIQUE, so a collision gets a short numeric suffix. */
    private String uniqueUsername(String email) {
        String base = AdminSlugs.slugify(email.split("@")[0]);
        if (base.isEmpty()) {
            base = "user";
        }
        String candidate = base;
        for (int attempt = 1; attempt <= 50; attempt++) {
            long taken = jdbc.sql("SELECT COUNT(*) FROM users WHERE username = ?")
                    .param(candidate).query(Long.class).optional().orElse(0L);
            if (taken == 0) {
                return candidate;
            }
            candidate = base + attempt;
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static UserRole resolveRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return UserRole.READER;
        }
        boolean admin = roles.stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.trim()));
        return admin ? UserRole.ADMIN : UserRole.READER;
    }

    private static String displayNameOrEmailPrefix(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return email.split("@")[0];
    }

    private User find(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "user.not_found", "User not found", "User not found"));
    }

    private AdminUserRow toRow(User user, String bio) {
        long coinBalance = jdbc.sql("SELECT COALESCE(coin_balance, 0) FROM wallets WHERE user_id = ?")
                .param(user.getId().toString()).query(Long.class).optional().orElse(0L);

        return new AdminUserRow(
                user.getId().toString(),
                user.getEmail(),
                AdminCategoryController.nullToEmpty(user.getDisplayName()),
                AdminCategoryController.nullToEmpty(bio),
                user.getStatus().name(),
                user.getRole().name(),
                coinBalance,
                user.getCreatedAt() == null ? null : user.getCreatedAt().toString(),
                user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString()
        );
    }
}
