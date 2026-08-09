package com.storyplatform.auth.infrastructure;

import com.storyplatform.auth.domain.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserRepository extends CrudRepository<User, UUID> {
    java.util.Optional<User> findByEmail(String email);
    java.util.Optional<User> findByUsername(String username);
}
