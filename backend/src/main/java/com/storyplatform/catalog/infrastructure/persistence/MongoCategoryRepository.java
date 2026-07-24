package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.port.CategoryRepository;
import com.storyplatform.catalog.domain.Category;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class MongoCategoryRepository implements CategoryRepository {

    private final MongoTemplate mongo;

    public MongoCategoryRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<Category> findActiveOrdered() {
        Query query = Query.query(Criteria.where("active").is(true))
                .with(Sort.by(
                        Sort.Order.asc("groupOrder"),
                        Sort.Order.asc("sortOrder"),
                        Sort.Order.asc("slug")
                ));
        return mongo.find(query, MongoCategoryDocument.class).stream()
                .map(MongoCategoryDocument::toDomain)
                .toList();
    }
}
