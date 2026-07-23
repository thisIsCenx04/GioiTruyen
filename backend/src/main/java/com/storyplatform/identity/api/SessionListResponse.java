package com.storyplatform.identity.api;

import java.util.List;

public record SessionListResponse(List<SessionResponse> sessions) {

    public SessionListResponse {
        sessions = List.copyOf(sessions);
    }
}
