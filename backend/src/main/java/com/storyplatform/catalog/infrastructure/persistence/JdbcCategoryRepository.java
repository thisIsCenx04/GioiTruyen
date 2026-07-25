package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.port.CategoryRepository;
import com.storyplatform.catalog.domain.Category;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class JdbcCategoryRepository implements CategoryRepository {

    private final JdbcClient jdbc;

    public JdbcCategoryRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<Category> findActiveOrdered() {
        return jdbc.sql("""
                        SELECT id, slug, name, group_key, sort_order,
                               active, version
                        FROM categories
                        WHERE active = TRUE
                        ORDER BY group_order, sort_order, slug
                        """)
                .query((result, rowNumber) -> new Category(
                        result.getString("id"),
                        result.getString("slug"),
                        result.getString("name"),
                        Category.Group.valueOf(
                                result.getString("group_key")
                        ),
                        result.getInt("sort_order"),
                        result.getBoolean("active"),
                        result.getLong("version")
                ))
                .list();
    }
}
