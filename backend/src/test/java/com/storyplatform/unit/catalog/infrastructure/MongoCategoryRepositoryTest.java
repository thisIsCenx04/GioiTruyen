package com.storyplatform.unit.catalog.infrastructure;

import com.storyplatform.catalog.domain.Category;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoCategoryDocument;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoCategoryRepository;
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

class MongoCategoryRepositoryTest {

    @Test
    void readsOnlyActiveCategoriesUsingTheCoveringOrder() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoCategoryDocument document = new MongoCategoryDocument(
                "id-1",
                "fantasy",
                "Kỳ ảo",
                Category.Group.GENRE,
                10,
                20,
                true,
                1
        );
        when(mongo.find(
                any(Query.class),
                eq(MongoCategoryDocument.class)
        )).thenReturn(List.of(document));
        MongoCategoryRepository repository =
                new MongoCategoryRepository(mongo);

        assertThat(repository.findActiveOrdered())
                .singleElement()
                .extracting(Category::slug)
                .isEqualTo("fantasy");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(
                query.capture(),
                eq(MongoCategoryDocument.class)
        );
        assertThat(query.getValue().getQueryObject().get("active"))
                .isEqualTo(true);
        assertThat(query.getValue().getSortObject().keySet())
                .containsExactly("groupOrder", "sortOrder", "slug");
    }
}
