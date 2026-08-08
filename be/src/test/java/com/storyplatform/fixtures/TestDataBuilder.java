package com.storyplatform.fixtures;

@FunctionalInterface
public interface TestDataBuilder<T> {

	T build();
}
