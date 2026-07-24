package com.storyplatform.catalog.application.port;

import com.storyplatform.catalog.domain.Category;

import java.util.List;

public interface CategoryRepository {

    List<Category> findActiveOrdered();
}
