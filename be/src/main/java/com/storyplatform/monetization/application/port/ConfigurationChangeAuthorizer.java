package com.storyplatform.monetization.application.port;

public interface ConfigurationChangeAuthorizer {

    boolean consume(String actorId, String rawToken);
}
