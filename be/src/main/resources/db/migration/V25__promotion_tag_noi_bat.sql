-- The badge on a promoted card now reads "Nổi bật" instead of "Bố cáo".
--
-- "Bố cáo" is the operator's word for the product a team buys; the reader
-- looking at the shelf has no reason to know what it means. "Nổi bật" says
-- what the card is to them - a featured story - and the label lives in the
-- database rather than the component, so it changes here.
--
-- Only rows still carrying the old default are touched. A booking whose label
-- an admin set by hand keeps whatever they wrote.
ALTER TABLE `story_promotions`
    MODIFY COLUMN `tag_label` VARCHAR(60) NOT NULL DEFAULT 'Nổi bật';

UPDATE `story_promotions`
SET `tag_label` = 'Nổi bật'
WHERE `tag_label` = 'Bố cáo';
