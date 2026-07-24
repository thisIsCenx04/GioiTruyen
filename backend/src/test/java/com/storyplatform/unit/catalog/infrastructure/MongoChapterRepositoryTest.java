package com.storyplatform.unit.catalog.infrastructure;

import com.storyplatform.catalog.application.PublicChapterProjection;
import com.storyplatform.catalog.application.port.ChapterRepository;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterDocument;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoChapterRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoChapterRepositoryTest {

    @Test
    void forcesPublishedStatusAndUsesAContentFreeProjection() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(PublicChapterProjection.class),
                eq(MongoChapterDocument.COLLECTION)
        )).thenReturn(List.of());
        var repository = new MongoChapterRepository(mongo);

        repository.findPublished(new ChapterRepository.ChapterListQuery(
                "10000000-0000-4000-8000-000000000001",
                10,
                "20000000-0000-4000-8000-000000000001",
                21
        ));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(
                query.capture(),
                eq(PublicChapterProjection.class),
                eq(MongoChapterDocument.COLLECTION)
        );
        assertThat(query.getValue().getQueryObject().toString())
                .contains("workflowStatus=PUBLISHED")
                .contains("storyId=");
        assertThat(query.getValue().getFieldsObject().keySet())
                .contains("_id", "storyId", "number", "publishedAt")
                .doesNotContain(
                        "teamId",
                        "workflowStatus",
                        "currentRevision",
                        "scheduledAt",
                        "content"
                );
    }
}
