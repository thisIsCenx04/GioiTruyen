package com.storyplatform.discovery.infrastructure.persistence;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasStorySearchRepositoryMappingTest {

    @Test
    void mapsScoresBoundedHitHighlightsAndAllFacets() {
        Document result = story()
                .append("publishedAt", Date.from(Instant.EPOCH))
                .append("score", 4.5)
                .append("highlights", List.of(new Document(
                        "texts",
                        List.of(
                                new Document("type", "hit")
                                        .append("value", "Story"),
                                new Document("type", "text")
                                        .append("value", "ignored")
                        )
                )));
        Document facet = new Document(
                "categoryIds",
                buckets("category-1", 4)
        ).append(
                "completionStatus",
                buckets("COMPLETED", 3)
        ).append("origin", buckets("ORIGINAL", 2));

        var hit = AtlasStorySearchRepository.hit(result);
        var facets = AtlasStorySearchRepository.facets(
                new Document("facet", facet)
        );

        assertThat(hit.score()).isEqualTo(4.5);
        assertThat(hit.highlights()).containsExactly("Story");
        assertThat(facets.get("categoryIds"))
                .containsEntry("category-1", 4L);
        assertThat(facets.get("completionStatus"))
                .containsEntry("COMPLETED", 3L);
        assertThat(facets.get("origin"))
                .containsEntry("ORIGINAL", 2L);
    }

    @Test
    void handlesOptionalMetadataAndRejectsMissingPublicationDate() {
        var hit = AtlasStorySearchRepository.hit(
                story().append("publishedAt", Instant.EPOCH)
        );

        assertThat(hit.score()).isZero();
        assertThat(hit.highlights()).isEmpty();
        assertThat(AtlasStorySearchRepository.facets(new Document()))
                .isEmpty();
        assertThat(AtlasStorySearchRepository.facets(new Document(
                "facet",
                new Document("categoryIds", new Document())
        ))).containsEntry("categoryIds", java.util.Map.of());
        assertThatThrownBy(() ->
                AtlasStorySearchRepository.hit(story()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Document story() {
        return new Document("_id",
                "10000000-0000-4000-8000-000000000001")
                .append("teamId",
                        "20000000-0000-4000-8000-000000000001")
                .append("slug", "story")
                .append("title", "Story");
    }

    private static Document buckets(
            String identifier,
            long count
    ) {
        return new Document("buckets", List.of(
                new Document("_id", identifier).append("count", count)
        ));
    }
}
