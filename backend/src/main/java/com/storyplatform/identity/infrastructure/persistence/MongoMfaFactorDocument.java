package com.storyplatform.identity.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoMfaFactorDocument.COLLECTION)
public record MongoMfaFactorDocument(
        @Id String userId,
        String protectedSecret,
        boolean enabled,
        List<String> recoveryCodeHashes,
        Instant createdAt,
        Instant activatedAt,
        Instant updatedAt
) {

    public static final String COLLECTION = "mfa_factors";
}
