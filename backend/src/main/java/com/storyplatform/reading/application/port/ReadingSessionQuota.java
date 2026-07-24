package com.storyplatform.reading.application.port;

public interface ReadingSessionQuota {

    boolean allow(String actorRef);

    long retryAfterSeconds();
}
