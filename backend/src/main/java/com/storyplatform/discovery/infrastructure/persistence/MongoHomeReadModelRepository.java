package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.HomeReadModel;
import com.storyplatform.discovery.application.port.HomeReadModelRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoHomeReadModelRepository
        implements HomeReadModelRepository {

    public static final String COLLECTION = "home_read_models";

    private final MongoTemplate mongo;

    public MongoHomeReadModelRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Optional<HomeReadModel> find(String locale) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("_id").is(locale)),
                HomeReadModel.class,
                COLLECTION
        ));
    }

    @Override
    public void replace(HomeReadModel model) {
        Update update = new Update()
                .set("locale", model.locale())
                .set("version", model.version())
                .set("generatedAt", model.generatedAt())
                .set("sections", model.sections());
        mongo.upsert(
                Query.query(Criteria.where("_id").is(model.locale())),
                update,
                COLLECTION
        );
    }
}
