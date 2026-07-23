package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.domain.RefreshTokenFamily;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoRefreshTokenFamilyDocument.COLLECTION)
public record MongoRefreshTokenFamilyDocument(
        @Id String id,
        String userId,
        long securityVersion,
        String currentTokenHash,
        List<String> usedTokenHashes,
        int generation,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        Instant createdAt,
        Instant updatedAt
) {

    public static final String COLLECTION = "refresh_token_families";

    static MongoRefreshTokenFamilyDocument from(
            RefreshTokenFamily family
    ) {
        return new MongoRefreshTokenFamilyDocument(
                family.id(),
                family.userId(),
                family.securityVersion(),
                family.currentTokenHash(),
                family.usedTokenHashes(),
                family.generation(),
                family.expiresAt(),
                family.revokedAt(),
                family.revokeReason(),
                family.createdAt(),
                family.updatedAt()
        );
    }
}
