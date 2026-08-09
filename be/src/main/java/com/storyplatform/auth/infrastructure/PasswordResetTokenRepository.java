package com.storyplatform.auth.infrastructure;

import com.storyplatform.auth.domain.PasswordResetToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, UUID> {
}
