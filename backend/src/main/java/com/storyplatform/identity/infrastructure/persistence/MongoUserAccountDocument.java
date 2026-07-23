package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = MongoUserAccountDocument.COLLECTION)
public record MongoUserAccountDocument(
        @Id String id,
        String emailNormalized,
        String passwordHash,
        Set<GlobalRole> globalRoles,
        UserState state,
        long securityVersion,
        String acceptedConsentVersion,
        Instant consentAcceptedAt,
        Instant createdAt,
        Instant updatedAt,
        @Version Long version
) {

    public static final String COLLECTION = "users";

    static MongoUserAccountDocument from(UserAccount account) {
        return new MongoUserAccountDocument(
                account.id(),
                account.emailNormalized(),
                account.passwordHash(),
                account.globalRoles(),
                account.state(),
                account.securityVersion(),
                account.acceptedConsentVersion(),
                account.consentAcceptedAt(),
                account.createdAt(),
                account.updatedAt(),
                null
        );
    }
}
