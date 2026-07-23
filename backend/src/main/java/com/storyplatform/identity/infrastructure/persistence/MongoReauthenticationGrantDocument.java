package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.ReauthenticationScope;
import com.storyplatform.identity.application.port
        .ReauthenticationGrantRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoReauthenticationGrantDocument.COLLECTION)
public record MongoReauthenticationGrantDocument(
        @Id String id,
        String tokenHash,
        String actorId,
        ReauthenticationScope scope,
        String targetType,
        String targetId,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {

    public static final String COLLECTION = "reauthentication_grants";

    static MongoReauthenticationGrantDocument from(
            ReauthenticationGrantRepository.Grant grant
    ) {
        return new MongoReauthenticationGrantDocument(
                grant.id(),
                grant.tokenHash(),
                grant.actorId(),
                grant.scope(),
                grant.targetType(),
                grant.targetId(),
                grant.expiresAt(),
                grant.consumedAt(),
                grant.createdAt()
        );
    }
}
