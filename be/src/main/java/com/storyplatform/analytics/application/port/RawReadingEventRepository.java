package com.storyplatform.analytics.application.port;

import com.storyplatform.analytics.application.RawReadingEvent;

import java.util.List;

public interface RawReadingEventRepository {

    void append(List<RawReadingEvent> events);
}
