-- Becoming a publisher: a reader asks, an admin decides.
--
-- Publishing rights previously existed only if an admin created a team by hand,
-- so a reader who wanted to post a story had no way to ask. An approval creates
-- the team for them - a team may hold one member or many, so this is the first
-- member rather than a special kind of account.

CREATE TABLE IF NOT EXISTS `author_applications` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    -- What the applicant wants their team to be called; the team is created
    -- from this on approval.
    team_name VARCHAR(160) NOT NULL,
    pen_name VARCHAR(120) NULL,
    introduction VARCHAR(2000) NOT NULL,
    sample_work VARCHAR(2000) NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    review_note VARCHAR(500) NULL,
    reviewed_by VARCHAR(36) NULL,
    reviewed_at TIMESTAMP(3) NULL,
    -- Set when approval creates the team, so the decision can be traced.
    created_team_id VARCHAR(36) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    -- No generated column here: MySQL refuses ON DELETE CASCADE / SET NULL on a
    -- column that a stored generated column reads from, which is why the
    -- obvious "unique while pending" index cannot be expressed in the schema.
    -- AuthorApplicationService enforces one open request per account instead.
    KEY idx_author_applications_status (status, created_at),
    KEY idx_author_applications_user (user_id, status),
    CONSTRAINT fk_author_applications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_author_applications_team FOREIGN KEY (created_team_id) REFERENCES teams(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
