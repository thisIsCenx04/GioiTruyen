package com.storyplatform.identity.api;

public record RegistrationResponse(
        String status,
        String message
) {

    public static RegistrationResponse accepted() {
        return new RegistrationResponse(
                "REGISTRATION_ACCEPTED",
                "If the address is available, the account is ready to use."
        );
    }
}
