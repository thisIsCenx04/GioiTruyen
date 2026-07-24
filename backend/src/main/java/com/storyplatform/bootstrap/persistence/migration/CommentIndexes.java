package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.community.infrastructure.persistence
        .MongoCommentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class CommentIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "810a67887c3309346ff9502e505ad1c92314c0ef6cd6fa20284717a88d103c43";

    @Override
    public long version() {
        return 34;
    }

    @Override
    public String name() {
        return "index safe comment lifecycle";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoCommentRepository.COLLECTION)
                .createIndex(new Index()
                        .named("comment_target_created")
                        .on("targetType", Sort.Direction.ASC)
                        .on("targetId", Sort.Direction.ASC)
                        .on("status", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
        mongo.indexOps(MongoCommentRepository.COLLECTION)
                .createIndex(new Index()
                        .named("comment_author_duplicate")
                        .on("authorId", Sort.Direction.ASC)
                        .on("targetType", Sort.Direction.ASC)
                        .on("targetId", Sort.Direction.ASC)
                        .on("bodyFingerprint", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
        mongo.indexOps(MongoCommentRepository.COLLECTION)
                .createIndex(new Index()
                        .named("comment_parent")
                        .on("parentId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC));
    }
}
