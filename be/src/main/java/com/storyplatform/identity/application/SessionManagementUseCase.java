package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SessionManagementUseCase {

    public static final String USER_REVOKED_REASON = "USER_REVOKED";

    private final RefreshTokenFamilyRepository families;
    private final Clock clock;

    public SessionManagementUseCase(
            RefreshTokenFamilyRepository families,
            Clock clock
    ) {
        this.families = Objects.requireNonNull(families, "families");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<SessionView> list(String userId, String currentSessionId) {
        return families.findActiveByUser(userId, clock.instant())
                .stream()
                .map(session -> new SessionView(
                        session.id(),
                        session.createdAt(),
                        session.lastUsedAt(),
                        session.expiresAt(),
                        session.id().equals(currentSessionId)
                ))
                .sorted(Comparator.comparing(
                        SessionView::lastUsedAt
                ).reversed())
                .toList();
    }

    public void revokeCurrent(String userId, String sessionId) {
        revoke(userId, sessionId);
    }

    public void revokeSpecific(String userId, String sessionId) {
        revoke(userId, sessionId);
    }

    public void revokeAll(String userId) {
        Instant now = clock.instant();
        families.revokeAllOwned(
                userId,
                now,
                USER_REVOKED_REASON
        );
    }

    private void revoke(String userId, String sessionId) {
        families.revokeOwned(
                sessionId,
                userId,
                clock.instant(),
                USER_REVOKED_REASON
        );
    }
}
