-- Records that a reader accepted the terms when they signed up, and which
-- version they saw.
--
-- The sign-up form already asks for the agreement and names a version, but
-- nothing stored the answer: the checkbox was decorative, and the API rejected
-- the field outright because no request record declared it. Keeping the version
-- alongside the timestamp is the point - "they agreed" is not answerable later
-- unless it says what they agreed to.
--
-- Nullable on purpose: accounts created before this migration never gave an
-- answer, and Google sign-in creates an account without showing the form.
ALTER TABLE `users`
    ADD COLUMN `terms_accepted_at` TIMESTAMP NULL AFTER `email_verified_at`,
    ADD COLUMN `terms_version` VARCHAR(32) NULL AFTER `terms_accepted_at`;
