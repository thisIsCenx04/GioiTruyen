package com.storyplatform.bootstrap;

import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.teams.application.port.TeamInvitationTokenCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Objects;

@Configuration(proxyBeanMethods = false)
public class TeamInvitationTokenConfiguration {

    @Bean
    TeamInvitationTokenCodec teamInvitationTokenCodec(
            VerificationTokenCodec delegate
    ) {
        return new IdentityTokenAdapter(delegate);
    }

    private static final class IdentityTokenAdapter
            implements TeamInvitationTokenCodec {

        private final VerificationTokenCodec delegate;

        private IdentityTokenAdapter(VerificationTokenCodec delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public IssuedToken issue(String userId, Instant expiresAt) {
            var issued = delegate.issue(userId, expiresAt);
            return new IssuedToken(
                    issued.verificationId(),
                    issued.tokenHash()
            );
        }

        @Override
        public String hash(String rawToken) {
            return delegate.hash(rawToken);
        }

        @Override
        public boolean isWellFormed(String rawToken) {
            return delegate.isWellFormed(rawToken);
        }
    }
}
