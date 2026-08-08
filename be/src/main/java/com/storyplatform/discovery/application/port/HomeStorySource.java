package com.storyplatform.discovery.application.port;

import com.storyplatform.discovery.application.HomeStorySummary;

import java.util.List;

public interface HomeStorySource {

    List<HomeStorySummary> find(Filter filter, int limit);

    enum Filter {
        LATEST,
        COMPLETED,
        ORIGINAL
    }
}
