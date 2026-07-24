package com.storyplatform.unit.community.infrastructure;

import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.community.infrastructure
        .StoryRelationCounterReconciler;
import com.storyplatform.community.infrastructure.StoryRelationCounterStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryRelationCounterTest {

    @Test
    void reconcilesBothCountersFromUniqueRelations() {
        var relations = mock(StoryRelationRepository.class);
        var counters = mock(StoryRelationCounterStore.class);
        when(relations.count("story", StoryRelation.Type.FAVORITE))
                .thenReturn(7L);
        when(relations.count("story", StoryRelation.Type.FOLLOW))
                .thenReturn(3L);

        var result = new StoryRelationCounterReconciler(
                relations,
                counters
        ).reconcile("story");

        assertThat(result.favorites()).isEqualTo(7);
        assertThat(result.followers()).isEqualTo(3);
        verify(counters).reconcile("story", 7, 3);
    }
}
