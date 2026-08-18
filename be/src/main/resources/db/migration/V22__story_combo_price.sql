-- Adds the column the story-combo pricing has been reading all along.
--
-- V20 created `story_combo_purchases`, which records the price paid, but nothing
-- ever added a place to configure the price. MonetizationFlowService has always
-- selected `stories.combo_price_xu`, so every combo purchase failed with
-- "Unknown column 'combo_price_xu' in 'field list'" - a 500 on the buy button
-- rather than a visible misconfiguration.
--
-- NULL means "no bundle deal": the combo then costs exactly the sum of the
-- chapter prices, and the UI advertises no discount. A value below that sum is
-- a deliberate discount, and the percentage is derived from the difference.
ALTER TABLE `stories`
    ADD COLUMN `combo_price_xu` BIGINT NULL
    AFTER `recommendation_gem_cache`;

-- Guards against a negative price; zero is treated the same as NULL by the
-- application, which falls back to the chapter total.
ALTER TABLE `stories`
    ADD CONSTRAINT `story_combo_price_non_negative`
    CHECK (`combo_price_xu` IS NULL OR `combo_price_xu` >= 0);
