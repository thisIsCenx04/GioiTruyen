package com.storyplatform.unit.discovery.infrastructure;

import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.port.HomeStorySource;
import com.storyplatform.discovery.infrastructure.persistence
        .MongoHomeStorySource;
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

class MongoHomeStorySourceTest {

    @Test
    void usesPublishedFiltersAndABoundedPublicProjection() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(HomeStorySummary.class),
                eq("stories")
        )).thenReturn(List.of());

        new MongoHomeStorySource(mongo).find(
                HomeStorySource.Filter.COMPLETED,
                13
        );

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(
                query.capture(),
                eq(HomeStorySummary.class),
                eq("stories")
        );
        assertThat(query.getValue().getQueryObject().toString())
                .contains("workflowStatus=PUBLISHED")
                .contains("completionStatus=COMPLETED");
        assertThat(query.getValue().getFieldsObject().keySet())
                .contains("_id", "title", "coverAssetId")
                .doesNotContain(
                        "synopsis",
                        "aliases",
                        "currentRevision"
                );
        assertThat(query.getValue().getLimit()).isEqualTo(13);
    }
}
