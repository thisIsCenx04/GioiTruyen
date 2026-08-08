package com.storyplatform.identity.api;

import java.util.List;

public record MfaActivationResponse(List<String> recoveryCodes) {

    public MfaActivationResponse {
        recoveryCodes = List.copyOf(recoveryCodes);
    }
}
