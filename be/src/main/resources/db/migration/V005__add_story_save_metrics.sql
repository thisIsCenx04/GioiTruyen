ALTER TABLE story_engagement_metrics
    ADD COLUMN save_count BIGINT NOT NULL DEFAULT 0 AFTER recommendation_count;
