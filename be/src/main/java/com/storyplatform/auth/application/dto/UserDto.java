package com.storyplatform.auth.application.dto;

import com.storyplatform.auth.domain.UserRole;
import com.storyplatform.auth.domain.UserStatus;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String username,
    String displayName,
    String avatarUrl,
    UserRole role,
    UserStatus status
) {}
