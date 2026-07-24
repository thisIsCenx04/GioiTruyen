package com.storyplatform.analytics.application.port;

public interface ReadingSessionPseudonymizer {

    String pseudonymize(String sessionId);
}
