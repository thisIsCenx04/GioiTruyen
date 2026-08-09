package com.storyplatform.auth.application.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserDto user
) {}
