package com.storyplatform.discovery.application;

public interface HomeOperations {

    HomeReadModel get(String locale);

    HomeReadModel rebuild(String locale, String version);
}
