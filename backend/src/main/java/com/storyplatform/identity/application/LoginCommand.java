package com.storyplatform.identity.application;

public record LoginCommand(
        String email,
        String password,
        String clientAddress
) {
}
