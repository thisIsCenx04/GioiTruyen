package com.storyplatform.unit.bootstrap.persistence.migration;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.bootstrap.persistence.migration
        .CategoryTaxonomyMigration;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoCategoryDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryTaxonomyMigrationTest {

    @Test
    void seedsEveryGroupWithoutDuplicateGroupSlugPairs() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        IndexOperations indexes = mock(IndexOperations.class);
        when(mongo.indexOps(MongoCategoryDocument.COLLECTION))
                .thenReturn(indexes);
        when(mongo.upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoCategoryDocument.COLLECTION)
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        CategoryTaxonomyMigration migration =
                new CategoryTaxonomyMigration();

        migration.apply(mongo);

        assertThat(migration.version()).isEqualTo(12);
        verify(indexes, times(2)).createIndex(any(Index.class));
        ArgumentCaptor<Query> queries =
                ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(24)).upsert(
                queries.capture(),
                any(Update.class),
                eq(MongoCategoryDocument.COLLECTION)
        );
        var keys = new HashSet<String>();
        queries.getAllValues().forEach(query -> {
            @SuppressWarnings("unchecked")
            List<Document> criteria = (List<Document>) query
                    .getQueryObject()
                    .get("$and");
            Object group = criteria.stream()
                    .filter(part -> part.containsKey("group"))
                    .findFirst()
                    .orElseThrow()
                    .get("group");
            Object slug = criteria.stream()
                    .filter(part -> part.containsKey("slug"))
                    .findFirst()
                    .orElseThrow()
                    .get("slug");
            keys.add(group + ":" + slug);
        });
        assertThat(keys).hasSize(24);
    }
}
