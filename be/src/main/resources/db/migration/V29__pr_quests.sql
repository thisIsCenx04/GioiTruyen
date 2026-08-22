-- PR quests: a marketplace where a team pays readers to promote a story off
-- the platform - TikTok, YouTube, a Facebook page.
--
-- The work happens where the server cannot see it, so every safeguard here is
-- about paying fairly for something that cannot be verified automatically:
-- money is escrowed before the work starts, a human approves, and every step
-- carries a deadline so neither side can stall the other indefinitely.

CREATE TABLE IF NOT EXISTS `pr_quests` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    team_id VARCHAR(36) NOT NULL,
    story_id VARCHAR(36) NULL,
    created_by VARCHAR(36) NOT NULL,

    -- OPEN: anyone eligible claims a slot, first to commit wins.
    -- APPLY: readers apply and the owner picks, for work worth negotiating over.
    quest_kind ENUM('OPEN', 'APPLY') NOT NULL DEFAULT 'OPEN',
    platform ENUM('TIKTOK', 'YOUTUBE', 'FACEBOOK', 'OTHER') NOT NULL DEFAULT 'OTHER',

    title VARCHAR(180) NOT NULL,
    -- The KPI lives in the description as prose. A structured follower minimum
    -- was considered and dropped: a channel with no followers can still pull
    -- tens of thousands of views, so the number would exclude the wrong people.
    requirement TEXT NOT NULL,
    contact_channel VARCHAR(40) NULL,
    contact_handle VARCHAR(160) NULL,

    -- Money. reward_xu is per slot, allocated up front, so a creator who is
    -- approved is paid exactly what the quest advertised.
    reward_xu BIGINT NOT NULL CHECK (reward_xu > 0),
    slot_count INT NOT NULL CHECK (slot_count > 0),
    -- Slots taken. Incremented by a single conditional UPDATE, which is what
    -- makes simultaneous claims safe without an application-level lock.
    claimed_count INT NOT NULL DEFAULT 0 CHECK (claimed_count >= 0),
    approved_count INT NOT NULL DEFAULT 0 CHECK (approved_count >= 0),

    -- Held for creators, drawn down on each approval, remainder refunded.
    escrow_xu BIGINT NOT NULL DEFAULT 0 CHECK (escrow_xu >= 0),
    paid_xu BIGINT NOT NULL DEFAULT 0 CHECK (paid_xu >= 0),
    -- The percentage fee, reserved at publish and charged per approval. The
    -- unused remainder is refunded with the escrow.
    fee_rate_percent INT NOT NULL DEFAULT 0 CHECK (fee_rate_percent >= 0),
    fee_reserved_xu BIGINT NOT NULL DEFAULT 0 CHECK (fee_reserved_xu >= 0),
    fee_charged_xu BIGINT NOT NULL DEFAULT 0 CHECK (fee_charged_xu >= 0),
    -- Charged once at publish and never returned, even if nobody claims. This
    -- is what makes a team think before posting, and what makes it pointless
    -- to post quests purely to move coins to an account you control.
    publish_fee_xu BIGINT NOT NULL DEFAULT 0 CHECK (publish_fee_xu >= 0),

    status ENUM('DRAFT', 'OPEN', 'FULL', 'CLOSED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP(3) NULL,
    registration_ends_at TIMESTAMP(3) NULL,
    closed_at TIMESTAMP(3) NULL,

    -- Deadlines, in days, copied onto each claim when it is created so that
    -- changing the default later cannot move a deadline someone is already
    -- working to.
    submit_window_days INT NOT NULL DEFAULT 7 CHECK (submit_window_days > 0),
    review_window_days INT NOT NULL DEFAULT 7 CHECK (review_window_days > 0),
    content_hold_days INT NOT NULL DEFAULT 30 CHECK (content_hold_days >= 0),

    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    KEY idx_pr_quests_team (team_id, created_at),
    KEY idx_pr_quests_open (status, registration_ends_at),
    KEY idx_pr_quests_story (story_id),
    CONSTRAINT fk_pr_quests_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_quests_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE SET NULL,
    CONSTRAINT fk_pr_quests_author FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `pr_quest_claims` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    quest_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,

    -- PENDING exists only for APPLY quests: the reader has applied and holds no
    -- slot yet. Approving the application is what takes the slot.
    status ENUM('PENDING', 'DECLINED', 'CLAIMED', 'SUBMITTED',
                'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')
        NOT NULL DEFAULT 'CLAIMED',

    apply_note VARCHAR(1000) NULL,
    claimed_at TIMESTAMP(3) NULL,
    submit_due_at TIMESTAMP(3) NULL,

    submitted_at TIMESTAMP(3) NULL,
    submission_url VARCHAR(1000) NULL,
    submission_note VARCHAR(1000) NULL,
    review_due_at TIMESTAMP(3) NULL,

    reviewed_at TIMESTAMP(3) NULL,
    reviewed_by VARCHAR(36) NULL,
    -- Set when the deadline paid the creator instead of the owner doing it, so
    -- the history can say why.
    auto_approved BOOLEAN NOT NULL DEFAULT FALSE,
    reject_reason VARCHAR(1000) NULL,
    reject_contact_channel VARCHAR(40) NULL,
    reject_contact_handle VARCHAR(160) NULL,

    paid_xu BIGINT NOT NULL DEFAULT 0 CHECK (paid_xu >= 0),
    paid_at TIMESTAMP(3) NULL,

    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    -- One reader, one claim per quest. Enforced here rather than in code so a
    -- double-click on two tabs cannot produce two claims however the request
    -- races through the application.
    UNIQUE KEY uk_pr_claim_once (quest_id, user_id),
    KEY idx_pr_claims_user (user_id, created_at),
    KEY idx_pr_claims_due (status, submit_due_at),
    KEY idx_pr_claims_review (status, review_due_at),
    CONSTRAINT fk_pr_claims_quest FOREIGN KEY (quest_id) REFERENCES pr_quests(id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_claims_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Screenshots submitted with the link. A link can die or be made private after
-- approval; the screenshot is evidence of what was there when it was submitted.
CREATE TABLE IF NOT EXISTS `pr_claim_files` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    claim_id VARCHAR(36) NOT NULL,
    media_url TEXT NOT NULL,
    uploaded_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_pr_claim_files_claim (claim_id),
    CONSTRAINT fk_pr_claim_files_claim FOREIGN KEY (claim_id)
        REFERENCES pr_quest_claims(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Complaints, either direction: a creator who was not paid, or a team whose
-- creator deleted the post after being paid.
CREATE TABLE IF NOT EXISTS `pr_disputes` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    claim_id VARCHAR(36) NOT NULL,
    raised_by VARCHAR(36) NOT NULL,
    raised_role ENUM('CREATOR', 'TEAM') NOT NULL,
    reason TEXT NOT NULL,
    evidence_urls TEXT NULL,
    status ENUM('OPEN', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'OPEN',
    admin_id VARCHAR(36) NULL,
    admin_note TEXT NULL,
    penalty_xu BIGINT NOT NULL DEFAULT 0 CHECK (penalty_xu >= 0),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at TIMESTAMP(3) NULL,
    KEY idx_pr_disputes_status (status, created_at),
    KEY idx_pr_disputes_claim (claim_id),
    CONSTRAINT fk_pr_disputes_claim FOREIGN KEY (claim_id)
        REFERENCES pr_quest_claims(id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_disputes_user FOREIGN KEY (raised_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Wallet movements get their own types rather than borrowing PURCHASE and
-- DAILY_REWARD, so a reader's history reads as what actually happened and the
-- coins earned this way can be told apart from coins bought with money.
ALTER TABLE `wallet_transactions`
    MODIFY COLUMN `type` ENUM(
        'DEPOSIT',
        'PURCHASE',
        'DONATION',
        'EARNING',
        'RECOMMENDATION',
        'DAILY_REWARD',
        'REFERRAL_REWARD',
        'REFUND',
        'ADMIN_ADJUSTMENT',
        'WITHDRAWAL',
        'PR_ESCROW',
        'PR_REWARD'
    ) NOT NULL;
