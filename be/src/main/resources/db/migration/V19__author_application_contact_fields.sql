-- Contact information and anti-spam constraints for publisher applications.
--
-- Adding phone number and Facebook / Fanpage URL fields to author_applications
-- so that requests can be vetted and deduplicated across the platform.

ALTER TABLE `author_applications`
    ADD COLUMN `phone_number` VARCHAR(30) NULL AFTER `sample_work`,
    ADD COLUMN `facebook_url` VARCHAR(500) NULL AFTER `phone_number`,
    ADD KEY `idx_author_applications_phone` (`phone_number`, `status`),
    ADD KEY `idx_author_applications_fb` (`facebook_url`(255), `status`);
