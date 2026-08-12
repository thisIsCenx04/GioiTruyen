-- Team earnings land in the owner's wallet, and that movement needs a name.
--
-- Donations and chapter sales previously only bumped teams.revenue_coin_cache,
-- a reporting figure nobody can spend. The money now reaches the owner's
-- wallet, so the ledger needs a type that says where it came from - reusing
-- DEPOSIT would make real top-ups indistinguishable from platform earnings.

ALTER TABLE `wallet_transactions`
    MODIFY COLUMN `type` ENUM('DEPOSIT',
                              'PURCHASE',
                              'DONATION',
                              'RECOMMENDATION',
                              'DAILY_REWARD',
                              'REFERRAL_REWARD',
                              'REFUND',
                              'EARNING',
                              'ADMIN_ADJUSTMENT') NOT NULL;
