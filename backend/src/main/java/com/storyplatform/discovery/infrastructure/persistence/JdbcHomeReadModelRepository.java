package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.HomeReadModel;
import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.port.HomeReadModelRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcHomeReadModelRepository
        implements HomeReadModelRepository {

    private final JdbcClient jdbc;

    public JdbcHomeReadModelRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<HomeReadModel> find(String locale) {
        return jdbc.sql("""
                        SELECT version, generated_at
                        FROM home_read_models
                        WHERE locale = :locale
                        """)
                .param("locale", locale)
                .query((result, rowNumber) -> new HomeReadModel(
                        locale,
                        result.getString("version"),
                        result.getTimestamp("generated_at").toInstant(),
                        loadSections(locale)
                ))
                .optional();
    }

    @Override
    @Transactional
    public void replace(HomeReadModel model) {
        jdbc.sql("""
                        INSERT INTO home_read_models (
                            locale, version, generated_at
                        ) VALUES (:locale, :version, :generatedAt)
                        ON DUPLICATE KEY UPDATE
                            version = VALUES(version),
                            generated_at = VALUES(generated_at)
                        """)
                .param("locale", model.locale())
                .param("version", model.version())
                .param("generatedAt", model.generatedAt())
                .update();
        jdbc.sql("""
                        DELETE FROM home_read_model_stories
                        WHERE locale = :locale
                        """)
                .param("locale", model.locale())
                .update();
        model.sections().forEach(section -> {
            for (int index = 0; index < section.stories().size(); index++) {
                insertStory(model.locale(), section, index);
            }
        });
    }

    private List<HomeReadModel.HomeSection> loadSections(String locale) {
        List<StoredStory> stories = jdbc.sql("""
                        SELECT section_id, section_type, section_title,
                               story_id, team_id, slug, title,
                               cover_asset_id, published_at
                        FROM home_read_model_stories
                        WHERE locale = :locale
                        ORDER BY FIELD(
                            section_type,
                            'LATEST', 'COMPLETED', 'ORIGINAL'
                        ), position_index
                        """)
                .param("locale", locale)
                .query((result, rowNumber) -> new StoredStory(
                        result.getString("section_id"),
                        HomeReadModel.SectionType.valueOf(
                                result.getString("section_type")
                        ),
                        result.getString("section_title"),
                        new HomeStorySummary(
                                result.getString("story_id"),
                                result.getString("team_id"),
                                result.getString("slug"),
                                result.getString("title"),
                                result.getString("cover_asset_id"),
                                result.getTimestamp("published_at")
                                        .toInstant()
                        )
                ))
                .list();
        Map<String, SectionAccumulator> grouped = new LinkedHashMap<>();
        stories.forEach(story -> grouped.computeIfAbsent(
                story.sectionId(),
                ignored -> new SectionAccumulator(
                        story.type(),
                        story.sectionTitle()
                )
        ).stories().add(story.story()));
        return grouped.entrySet().stream()
                .map(entry -> new HomeReadModel.HomeSection(
                        entry.getKey(),
                        entry.getValue().type(),
                        entry.getValue().title(),
                        entry.getValue().stories()
                ))
                .toList();
    }

    private void insertStory(
            String locale,
            HomeReadModel.HomeSection section,
            int index
    ) {
        HomeStorySummary story = section.stories().get(index);
        jdbc.sql("""
                        INSERT INTO home_read_model_stories (
                            locale, section_id, section_type, section_title,
                            position_index, story_id, team_id, slug, title,
                            cover_asset_id, published_at
                        ) VALUES (
                            :locale, :sectionId, :sectionType, :sectionTitle,
                            :position, :storyId, :teamId, :slug, :title,
                            :coverAssetId, :publishedAt
                        )
                        """)
                .param("locale", locale)
                .param("sectionId", section.id())
                .param("sectionType", section.type().name())
                .param("sectionTitle", section.title())
                .param("position", index)
                .param("storyId", story.id())
                .param("teamId", story.teamId())
                .param("slug", story.slug())
                .param("title", story.title())
                .param("coverAssetId", story.coverAssetId())
                .param("publishedAt", story.publishedAt())
                .update();
    }

    private record StoredStory(
            String sectionId,
            HomeReadModel.SectionType type,
            String sectionTitle,
            HomeStorySummary story
    ) {
    }

    private record SectionAccumulator(
            HomeReadModel.SectionType type,
            String title,
            List<HomeStorySummary> stories
    ) {
        private SectionAccumulator(
                HomeReadModel.SectionType type,
                String title
        ) {
            this(type, title, new java.util.ArrayList<>());
        }
    }
}
