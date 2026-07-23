package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.PasswordResetRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoPasswordResetDocument.COLLECTION)
public record MongoPasswordResetDocument(
        @Id String id,
        String userId,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {

    public static final String COLLECTION = "password_reset_tokens";

    static MongoPasswordResetDocument from(
            PasswordResetRepository.PasswordReset reset
    ) {
        return new MongoPasswordResetDocument(
                reset.id(),
                reset.userId(),
                reset.tokenHash(),
                reset.expiresAt(),
                reset.consumedAt(),
                reset.createdAt()
        );
    }

    PasswordResetRepository.PasswordReset toDomain() {
        return new PasswordResetRepository.PasswordReset(
                id,
                userId,
                tokenHash,
                expiresAt,
                consumedAt,
                createdAt
        );
    }
}
