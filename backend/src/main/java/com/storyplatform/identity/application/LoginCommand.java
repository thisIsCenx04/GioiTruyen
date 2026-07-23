package com.storyplatform.identity.application;

public record LoginCommand(
        String email,
        String password,
        String clientAddress,
        String mfaCode
) {
    public LoginCommand(
            String email,
            String password,
            String clientAddress
    ) {
        this(email, password, clientAddress, null);
    }
}
