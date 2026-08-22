-- Owner payouts and the wallet entries that back them.
--
-- Team income is credited into the owner's wallet as EARNING rows. A withdrawal
-- reserves coins from that same wallet and leaves an auditable WITHDRAWAL row,
-- while the bank details stay in the withdrawal request table for admin payout.

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
        'WITHDRAWAL'
    ) NOT NULL;

CREATE TABLE IF NOT EXISTS `withdrawal_requests` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    team_id VARCHAR(36) NULL,
    account_name VARCHAR(160) NOT NULL,
    account_number VARCHAR(80) NOT NULL,
    bank_name VARCHAR(120) NOT NULL,
    gross_amount_xu BIGINT NOT NULL CHECK (gross_amount_xu > 0),
    fee_xu BIGINT NOT NULL DEFAULT 0 CHECK (fee_xu >= 0),
    net_amount_xu BIGINT NOT NULL CHECK (net_amount_xu >= 0),
    state ENUM('PENDING_REVIEW', 'APPROVED', 'PROCESSING', 'PAID', 'REJECTED', 'FAILED')
        NOT NULL DEFAULT 'PENDING_REVIEW',
    note VARCHAR(500) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_withdrawal_requests_user (user_id, created_at),
    KEY idx_withdrawal_requests_team (team_id, created_at),
    KEY idx_withdrawal_requests_state (state, created_at),
    CONSTRAINT fk_withdrawal_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_withdrawal_requests_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
