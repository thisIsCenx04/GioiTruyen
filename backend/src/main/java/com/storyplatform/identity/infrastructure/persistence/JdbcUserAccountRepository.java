package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {

    private static final String SELECT_ACCOUNT = """
            SELECT id, email_normalized, password_hash, state,
                   security_version, accepted_consent_version,
                   consent_accepted_at, created_at, updated_at, version
            FROM users
            """;

    private final JdbcClient jdbc;

    public JdbcUserAccountRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public boolean saveIfEmailAvailable(UserAccount account) {
        Objects.requireNonNull(account, "account");
        try {
            int inserted = jdbc.sql("""
                            INSERT INTO users (
                                id, email_normalized, password_hash, state,
                                security_version, accepted_consent_version,
                                consent_accepted_at, created_at, updated_at,
                                version
                            ) VALUES (
                                :id, :email, :passwordHash, :state,
                                :securityVersion, :consentVersion,
                                :consentAcceptedAt, :createdAt, :updatedAt,
                                :version
                            )
                            """)
                    .param("id", account.id())
                    .param("email", account.emailNormalized())
                    .param("passwordHash", account.passwordHash())
                    .param("state", account.state().name())
                    .param("securityVersion", account.securityVersion())
                    .param(
                            "consentVersion",
                            account.acceptedConsentVersion()
                    )
                    .param(
                            "consentAcceptedAt",
                            account.consentAcceptedAt()
                    )
                    .param("createdAt", account.createdAt())
                    .param("updatedAt", account.updatedAt())
                    .param("version", account.version())
                    .update();
            account.globalRoles().forEach(role -> jdbc.sql("""
                            INSERT INTO user_roles (user_id, role)
                            VALUES (:userId, :role)
                            """)
                    .param("userId", account.id())
                    .param("role", role.name())
                    .update());
            return inserted == 1;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public boolean activatePending(String userId, Instant activatedAt) {
        return jdbc.sql("""
                        UPDATE users
                        SET state = 'ACTIVE',
                            updated_at = :activatedAt,
                            version = version + 1
                        WHERE id = :userId
                          AND state = 'PENDING_EMAIL_VERIFICATION'
                        """)
                .param("userId", userId)
                .param("activatedAt", activatedAt)
                .update() == 1;
    }

    @Override
    public boolean resetPassword(
            String userId,
            String passwordHash,
            Instant changedAt
    ) {
        return jdbc.sql("""
                        UPDATE users
                        SET password_hash = :passwordHash,
                            updated_at = :changedAt,
                            security_version = security_version + 1,
                            version = version + 1
                        WHERE id = :userId AND state = 'ACTIVE'
                        """)
                .param("userId", userId)
                .param("passwordHash", passwordHash)
                .param("changedAt", changedAt)
                .update() == 1;
    }

    @Override
    public boolean incrementSecurityVersion(
            String userId,
            Instant changedAt
    ) {
        return jdbc.sql("""
                        UPDATE users
                        SET updated_at = :changedAt,
                            security_version = security_version + 1,
                            version = version + 1
                        WHERE id = :userId
                        """)
                .param("userId", userId)
                .param("changedAt", changedAt)
                .update() == 1;
    }

    @Override
    public Optional<UserAccount> findByEmail(String emailNormalized) {
        return jdbc.sql(SELECT_ACCOUNT
                        + " WHERE email_normalized = :email")
                .param("email", emailNormalized)
                .query(this::mapAccount)
                .optional();
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        return jdbc.sql(SELECT_ACCOUNT + " WHERE id = :id")
                .param("id", userId)
                .query(this::mapAccount)
                .optional();
    }

    private UserAccount mapAccount(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        String userId = result.getString("id");
        Set<GlobalRole> roles = jdbc.sql("""
                        SELECT role
                        FROM user_roles
                        WHERE user_id = :userId
                        ORDER BY role
                        """)
                .param("userId", userId)
                .query(String.class)
                .list()
                .stream()
                .map(GlobalRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new UserAccount(
                userId,
                result.getString("email_normalized"),
                result.getString("password_hash"),
                roles,
                UserState.valueOf(result.getString("state")),
                result.getLong("security_version"),
                result.getString("accepted_consent_version"),
                result.getTimestamp("consent_accepted_at").toInstant(),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version")
        );
    }
}
