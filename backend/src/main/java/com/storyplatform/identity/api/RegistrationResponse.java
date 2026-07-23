package com.storyplatform.identity.api;

public record RegistrationResponse(
        String status,
        String message
) {

    public static RegistrationResponse pendingVerification() {
        return new RegistrationResponse(
                "PENDING_VERIFICATION",
                "If the address is eligible, verification instructions "
                        + "will be sent."
        );
    }
}
