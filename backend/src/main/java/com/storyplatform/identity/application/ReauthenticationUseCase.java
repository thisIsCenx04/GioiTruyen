package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port
        .ReauthenticationGrantRepository;
import com.storyplatform.identity.application.port
        .ReauthenticationTokenCodec;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.identity.domain.UserAccount;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ReauthenticationUseCase
        implements ReauthenticationVerifier {

    private static final Pattern TARGET_TYPE =
            Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern TARGET_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final UserAccountRepository users;
    private final PasswordHasher passwords;
    private final MfaUseCase mfa;
    private final ReauthenticationTokenCodec tokens;
    private final ReauthenticationGrantRepository grants;
    private final Duration ttl;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public ReauthenticationUseCase(
            UserAccountRepository users,
            PasswordHasher passwords,
            MfaUseCase mfa,
            ReauthenticationTokenCodec tokens,
            ReauthenticationGrantRepository grants,
            Duration ttl,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.users = Objects.requireNonNull(users, "users");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.mfa = Objects.requireNonNull(mfa, "mfa");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "idGenerator"
        );
    }

    public IssueResult issue(IssueCommand command) {
        Objects.requireNonNull(command, "command");
        if (!validTarget(command.targetType(), command.targetId())) {
            return IssueResult.invalidTarget();
        }
        UserAccount account = users.findById(command.actorId())
                .filter(UserAccount::isActive)
                .orElse(null);
        if (account == null
                || !passwords.matches(
                        command.password(),
                        account.passwordHash()
                )
                || mfa.authenticate(
                        account,
                        command.mfaCode()
                ) != MfaUseCase.AuthenticationResult.VERIFIED) {
            return IssueResult.invalidProof();
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        ReauthenticationTokenCodec.IssuedToken issued = tokens.issue();
        grants.save(new ReauthenticationGrantRepository.Grant(
                idGenerator.get().toString(),
                issued.hash(),
                command.actorId(),
                command.scope(),
                command.targetType(),
                command.targetId(),
                expiresAt,
                null,
                now
        ));
        return IssueResult.issued(
                issued.value(),
                ttl.toSeconds(),
                expiresAt
        );
    }

    public boolean consume(
            String rawToken,
            String actorId,
            ReauthenticationScope scope,
            String targetType,
            String targetId
    ) {
        if (!tokens.isWellFormed(rawToken)
                || !validTarget(targetType, targetId)) {
            return false;
        }
        return grants.consume(
                tokens.hash(rawToken),
                actorId,
                scope,
                targetType,
                targetId,
                clock.instant()
        );
    }

    @Override
    public boolean consume(
            String rawToken,
            String actorId,
            String scope,
            String targetType,
            String targetId
    ) {
        try {
            return consume(
                    rawToken,
                    actorId,
                    ReauthenticationScope.valueOf(scope),
                    targetType,
                    targetId
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean validTarget(String type, String id) {
        return type != null
                && id != null
                && TARGET_TYPE.matcher(type).matches()
                && TARGET_ID.matcher(id).matches();
    }

    public record IssueCommand(
            String actorId,
            String password,
            String mfaCode,
            ReauthenticationScope scope,
            String targetType,
            String targetId
    ) {
        public IssueCommand {
            Objects.requireNonNull(scope, "scope");
        }
    }

    public record IssueResult(
            Status status,
            String token,
            long expiresInSeconds,
            Instant expiresAt
    ) {
        static IssueResult issued(
                String token,
                long expiresInSeconds,
                Instant expiresAt
        ) {
            return new IssueResult(
                    Status.ISSUED,
                    token,
                    expiresInSeconds,
                    expiresAt
            );
        }

        static IssueResult invalidProof() {
            return new IssueResult(
                    Status.INVALID_PROOF,
                    null,
                    0,
                    null
            );
        }

        static IssueResult invalidTarget() {
            return new IssueResult(
                    Status.INVALID_TARGET,
                    null,
                    0,
                    null
            );
        }
    }

    public enum Status {
        ISSUED,
        INVALID_PROOF,
        INVALID_TARGET
    }
}
