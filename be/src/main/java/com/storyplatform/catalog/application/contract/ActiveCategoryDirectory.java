package com.storyplatform.catalog.application.contract;

import java.util.Set;

@FunctionalInterface
public interface ActiveCategoryDirectory {

    Set<String> activeCategoryIds();
}
