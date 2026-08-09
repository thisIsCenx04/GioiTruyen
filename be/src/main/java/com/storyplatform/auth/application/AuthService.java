package com.storyplatform.auth.application;

import com.storyplatform.auth.application.dto.AuthResponse;
import com.storyplatform.auth.application.dto.LoginRequest;
import com.storyplatform.auth.application.dto.RegisterRequest;
import com.storyplatform.auth.application.dto.UserDto;
import com.storyplatform.auth.domain.User;
import com.storyplatform.auth.domain.UserRole;
import com.storyplatform.auth.domain.UserStatus;
import com.storyplatform.auth.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        // TODO: PasswordEncoder
        user.setPasswordHash(request.password());
        user.setRole(UserRole.READER);
        user.setStatus(UserStatus.ACTIVE);
        
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        user = userRepository.save(user);

        // TODO: Generate real tokens
        String accessToken = "mock_access_token";
        String refreshToken = "mock_refresh_token";

        return new AuthResponse(accessToken, refreshToken, mapToDto(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // TODO: Check password hash
        if (!user.getPasswordHash().equals(request.password())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // TODO: Generate real tokens
        String accessToken = "mock_access_token";
        String refreshToken = "mock_refresh_token";

        return new AuthResponse(accessToken, refreshToken, mapToDto(user));
    }

    private UserDto mapToDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus()
        );
    }
}
