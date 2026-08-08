package com.storyplatform.identity.application;

public record RegisterUserCommand(
        String email,
        String password,
        String consentVersion,
        String correlationId
) {
}
