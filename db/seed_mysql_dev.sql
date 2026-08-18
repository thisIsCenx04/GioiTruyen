-- ============================================================================
-- Gioitruyen - OBSOLETE SEED. DO NOT RUN.
--
-- This file targets db/entity_mysql_final.sql, which is a design reference and
-- NOT the deployed schema. It writes ids as BINARY(16) via UUID_TO_BIN(), while
-- the live Flyway schema uses VARCHAR(36); every INSERT here fails against the
-- real database. Running it is what makes local data look "out of sync".
--
-- The local seed of record is:
--     be/src/main/resources/db/seed/R__local_seed_data.sql
-- It is generated - rebuild it with `npm run seed:refresh`, and Flyway applies
-- it automatically on the "local" profile. Kept only for historical reference.
-- ============================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------------
-- Stable IDs
-- ---------------------------------------------------------------------------
SET @admin   = UUID_TO_BIN('00000000-0000-0000-0000-000000000001');
SET @user1   = UUID_TO_BIN('00000000-0000-0000-0000-000000000002');
SET @user2   = UUID_TO_BIN('00000000-0000-0000-0000-000000000003');
SET @user3   = UUID_TO_BIN('00000000-0000-0000-0000-000000000004');
SET @user4   = UUID_TO_BIN('00000000-0000-0000-0000-000000000005');

SET @team1   = UUID_TO_BIN('10000000-0000-0000-0000-000000000001');
SET @team2   = UUID_TO_BIN('10000000-0000-0000-0000-000000000002');

SET @genre1  = UUID_TO_BIN('20000000-0000-0000-0000-000000000001');
SET @genre2  = UUID_TO_BIN('20000000-0000-0000-0000-000000000002');
SET @genre3  = UUID_TO_BIN('20000000-0000-0000-0000-000000000003');
SET @genre4  = UUID_TO_BIN('20000000-0000-0000-0000-000000000004');

SET @story1  = UUID_TO_BIN('30000000-0000-0000-0000-000000000001');
SET @story2  = UUID_TO_BIN('30000000-0000-0000-0000-000000000002');
SET @story3  = UUID_TO_BIN('30000000-0000-0000-0000-000000000003');

SET @chap11  = UUID_TO_BIN('40000000-0000-0000-0000-000000000001');
SET @chap12  = UUID_TO_BIN('40000000-0000-0000-0000-000000000002');
SET @chap13  = UUID_TO_BIN('40000000-0000-0000-0000-000000000003');
SET @chap21  = UUID_TO_BIN('40000000-0000-0000-0000-000000000004');
SET @chap22  = UUID_TO_BIN('40000000-0000-0000-0000-000000000005');

-- ---------------------------------------------------------------------------
-- USERS / AUTH
-- Password hashes are intentionally NULL. Authentication-specific accounts
-- should be created through the application when testing real login flows.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO users
(id,email,username,password_hash,display_name,role,status,email_verified_at,last_login_at)
VALUES
(@admin,'admin@gioitruyen.local','admin',NULL,'Quản trị viên','ADMIN','ACTIVE',NOW(3),NOW(3)),
(@user1,'minh@gioitruyen.local','minhnguyen',NULL,'Minh Nguyễn','READER','ACTIVE',NOW(3),NOW(3)),
(@user2,'lan@gioitruyen.local','lanpham',NULL,'Lan Phạm','READER','ACTIVE',NOW(3),NOW(3)),
(@user3,'huy@gioitruyen.local','huytran',NULL,'Huy Trần','READER','ACTIVE',NOW(3),NOW(3)),
(@user4,'mai@gioitruyen.local','maile',NULL,'Mai Lê','READER','ACTIVE',NOW(3),NOW(3));

INSERT IGNORE INTO auth_accounts (id,user_id,provider,provider_account_id) VALUES
(UUID_TO_BIN('01000000-0000-0000-0000-000000000001'),@admin,'LOCAL','admin@gioitruyen.local'),
(UUID_TO_BIN('01000000-0000-0000-0000-000000000002'),@user1,'LOCAL','minh@gioitruyen.local'),
(UUID_TO_BIN('01000000-0000-0000-0000-000000000003'),@user2,'GOOGLE','google-seed-lan');

INSERT IGNORE INTO refresh_tokens (id,user_id,token_hash,expires_at,revoked_at) VALUES
(UUID_TO_BIN('01100000-0000-0000-0000-000000000001'),@user1,'seed-refresh-hash-1',DATE_ADD(NOW(3),INTERVAL 7 DAY),NULL),
(UUID_TO_BIN('01100000-0000-0000-0000-000000000002'),@user2,'seed-refresh-hash-2',DATE_ADD(NOW(3),INTERVAL 7 DAY),NOW(3));

INSERT IGNORE INTO password_reset_tokens (id,user_id,token_hash,expires_at,used_at) VALUES
(UUID_TO_BIN('01200000-0000-0000-0000-000000000001'),@user3,'seed-reset-hash-1',DATE_ADD(NOW(3),INTERVAL 30 MINUTE),NULL),
(UUID_TO_BIN('01200000-0000-0000-0000-000000000002'),@user4,'seed-reset-hash-2',DATE_SUB(NOW(3),INTERVAL 1 DAY),NULL);

INSERT IGNORE INTO email_verification_tokens (id,user_id,token_hash,expires_at,used_at) VALUES
(UUID_TO_BIN('01300000-0000-0000-0000-000000000001'),@user3,'seed-verify-hash-1',DATE_ADD(NOW(3),INTERVAL 1 DAY),NULL),
(UUID_TO_BIN('01300000-0000-0000-0000-000000000002'),@user4,'seed-verify-hash-2',DATE_ADD(NOW(3),INTERVAL 1 DAY),NOW(3));

INSERT IGNORE INTO user_profiles (user_id,bio,cover_url,gender,birthday,website_url) VALUES
(@admin,'Quản trị hệ thống',NULL,NULL,NULL,NULL),
(@user1,'Thích truyện huyền huyễn và tiên hiệp','/seed/covers/user1.webp','MALE','2000-05-10',NULL),
(@user2,'Đọc truyện mỗi tối','/seed/covers/user2.webp','FEMALE','2001-09-21',NULL),
(@user3,'Fan truyện trinh thám',NULL,'MALE','1999-03-15',NULL);

INSERT IGNORE INTO user_settings (user_id,theme,reader_font_size,reader_font_family,reader_line_height) VALUES
(@admin,'DARK',18,'Inter',1.70),
(@user1,'DARK',20,'Arial',1.80),
(@user2,'LIGHT',18,'Georgia',1.70),
(@user3,'SYSTEM',19,NULL,1.75);

-- ---------------------------------------------------------------------------
-- TEAMS
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO teams
(id,name,slug,description,status,created_by,follower_count_cache,story_count_cache,view_count_cache,revenue_coin_cache)
VALUES
(@team1,'Thiên Hà Team','thien-ha-team','Nhóm dịch truyện fantasy và huyền huyễn.','ACTIVE',@admin,2,2,12500,3600),
(@team2,'Mộc Miên Team','moc-mien-team','Nhóm biên tập truyện nhẹ nhàng và trinh thám.','ACTIVE',@admin,1,1,4300,1200);

INSERT IGNORE INTO team_members (id,team_id,user_id,member_role,status,added_by) VALUES
(UUID_TO_BIN('11000000-0000-0000-0000-000000000001'),@team1,@user1,'OWNER','ACTIVE',@admin),
(UUID_TO_BIN('11000000-0000-0000-0000-000000000002'),@team1,@user2,'EDITOR','ACTIVE',@admin),
(UUID_TO_BIN('11000000-0000-0000-0000-000000000003'),@team2,@user3,'OWNER','ACTIVE',@admin),
(UUID_TO_BIN('11000000-0000-0000-0000-000000000004'),@team2,@user4,'MEMBER','ACTIVE',@admin);

INSERT IGNORE INTO team_follows (user_id,team_id) VALUES
(@user2,@team1),(@user3,@team1),(@user1,@team2);

INSERT IGNORE INTO team_daily_stats
(team_id,stat_date,views,new_follows,published_stories,published_chapters,gross_coin,net_coin)
VALUES
(@team1,CURRENT_DATE,820,2,0,2,1000,900),
(@team1,DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),610,1,0,1,700,630),
(@team2,CURRENT_DATE,350,1,0,1,400,360);

-- ---------------------------------------------------------------------------
-- GENRES / STORIES
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO genres (id,name,slug,description,is_active) VALUES
(@genre1,'Tiên Hiệp','tien-hiep','Tu tiên, huyền huyễn và thế giới rộng lớn',TRUE),
(@genre2,'Fantasy','fantasy','Phiêu lưu trong thế giới giả tưởng',TRUE),
(@genre3,'Trinh Thám','trinh-tham','Điều tra, bí ẩn và suy luận',TRUE),
(@genre4,'Đời Thường','doi-thuong','Những câu chuyện gần gũi đời sống',TRUE);

INSERT IGNORE INTO stories
(id,team_id,created_by,title,slug,original_title,original_author,short_description,description,cover_url,content_type,status,progress_status,published_at,last_chapter_at,view_count_cache,follow_count_cache,favorite_count_cache,recommendation_gem_cache)
VALUES
(@story1,@team1,@user1,'Kiếm Khách Thiên Hà','kiem-khach-thien-ha','Galaxy Swordsman','Seed Author A','Một kiếm khách bước vào hành trình giữa các tinh vực.','Dữ liệu mẫu cho trang chi tiết truyện.','/seed/stories/kiem-khach.webp','TEXT_AUDIO','PUBLISHED','ONGOING',DATE_SUB(NOW(3),INTERVAL 30 DAY),NOW(3),8500,320,180,900),
(@story2,@team1,@user2,'Tháp Ma Pháp Cuối Cùng','thap-ma-phap-cuoi-cung','The Last Magic Tower','Seed Author B','Cuộc chiến tại tòa tháp ma pháp cuối cùng.','Truyện fantasy dùng để kiểm thử danh sách và ranking.','/seed/stories/magic-tower.webp','TEXT','PUBLISHED','ONGOING',DATE_SUB(NOW(3),INTERVAL 15 DAY),NOW(3),4000,150,90,350),
(@story3,@team2,@user3,'Án Mạng Trong Mưa','an-mang-trong-mua',NULL,'Seed Author C','Một vụ án bí ẩn giữa thành phố mưa.','Truyện mẫu đang chờ kiểm duyệt.','/seed/stories/rain-case.webp','TEXT','PENDING_REVIEW','COMPLETED',NULL,NULL,0,0,0,0);

INSERT IGNORE INTO story_genres (story_id,genre_id) VALUES
(@story1,@genre1),(@story1,@genre2),(@story2,@genre2),(@story3,@genre3),(@story3,@genre4);

INSERT IGNORE INTO story_reviews
(id,story_id,submitted_by,reviewed_by,status,admin_note,submitted_at,reviewed_at)
VALUES
(UUID_TO_BIN('31000000-0000-0000-0000-000000000001'),@story1,@user1,@admin,'APPROVED','Nội dung đạt yêu cầu.',DATE_SUB(NOW(3),INTERVAL 31 DAY),DATE_SUB(NOW(3),INTERVAL 30 DAY)),
(UUID_TO_BIN('31000000-0000-0000-0000-000000000002'),@story2,@user2,@admin,'APPROVED','Đã duyệt.',DATE_SUB(NOW(3),INTERVAL 16 DAY),DATE_SUB(NOW(3),INTERVAL 15 DAY)),
(UUID_TO_BIN('31000000-0000-0000-0000-000000000003'),@story3,@user3,NULL,'PENDING',NULL,NOW(3),NULL);

-- ---------------------------------------------------------------------------
-- CHAPTERS / AUDIO
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO chapters
(id,story_id,chapter_number,title,slug,content,short_description,access_type,coin_price,status,published_at,created_by)
VALUES
(@chap11,@story1,1.00,'Khởi đầu','chuong-1','Nội dung chương 1 dùng cho môi trường development.','Khởi đầu hành trình.','FREE',0,'PUBLISHED',DATE_SUB(NOW(3),INTERVAL 29 DAY),@user1),
(@chap12,@story1,2.00,'Tinh môn','chuong-2','Nội dung chương 2 có phí dùng để kiểm thử unlock.','Tinh môn xuất hiện.','PAID',100,'PUBLISHED',DATE_SUB(NOW(3),INTERVAL 20 DAY),@user1),
(@chap13,@story1,3.00,'Kiếm ý','chuong-3','Nội dung chương 3 có phí.','Kiếm ý thức tỉnh.','PAID',120,'PUBLISHED',DATE_SUB(NOW(3),INTERVAL 5 DAY),@user1),
(@chap21,@story2,1.00,'Tòa tháp','chuong-1','Nội dung chương mở đầu của Tháp Ma Pháp.','Phát hiện tòa tháp.','FREE',0,'PUBLISHED',DATE_SUB(NOW(3),INTERVAL 14 DAY),@user2),
(@chap22,@story2,2.00,'Tầng thứ hai','chuong-2','Nội dung chương trả phí của Tháp Ma Pháp.','Tiến lên tầng hai.','PAID',80,'PUBLISHED',DATE_SUB(NOW(3),INTERVAL 2 DAY),@user2);

SET @audio1 = UUID_TO_BIN('41000000-0000-0000-0000-000000000001');
SET @audio2 = UUID_TO_BIN('41000000-0000-0000-0000-000000000002');
INSERT IGNORE INTO chapter_audios (id,chapter_id,audio_url,duration_seconds,file_size,narrator,status) VALUES
(@audio1,@chap11,'/seed/audio/story1-ch1.mp3',620,8500000,'Seed Voice A','VISIBLE'),
(@audio2,@chap12,'/seed/audio/story1-ch2.mp3',740,9700000,'Seed Voice A','VISIBLE');

INSERT IGNORE INTO audio_listens (id,chapter_audio_id,user_id,session_id,listened_seconds) VALUES
(UUID_TO_BIN('42000000-0000-0000-0000-000000000001'),@audio1,@user2,'seed-session-001',500),
(UUID_TO_BIN('42000000-0000-0000-0000-000000000002'),@audio1,@user3,'seed-session-002',620),
(UUID_TO_BIN('42000000-0000-0000-0000-000000000003'),@audio2,@user2,'seed-session-003',300);

-- ---------------------------------------------------------------------------
-- ENGAGEMENT
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO story_views (id,story_id,chapter_id,user_id,session_id,ip_hash,viewed_at) VALUES
(UUID_TO_BIN('50000000-0000-0000-0000-000000000001'),@story1,@chap11,@user2,'seed-session-001','seed-ip-1',DATE_SUB(NOW(3),INTERVAL 2 HOUR)),
(UUID_TO_BIN('50000000-0000-0000-0000-000000000002'),@story1,@chap12,@user3,'seed-session-002','seed-ip-2',DATE_SUB(NOW(3),INTERVAL 1 HOUR)),
(UUID_TO_BIN('50000000-0000-0000-0000-000000000003'),@story2,@chap21,@user1,'seed-session-004','seed-ip-3',NOW(3));

INSERT IGNORE INTO story_daily_stats
(story_id,stat_date,views,unique_views,audio_listens,favorites,follows,recommendations,coin_revenue)
VALUES
(@story1,CURRENT_DATE,820,610,120,20,15,70,900),
(@story1,DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),640,500,95,12,10,40,650),
(@story2,CURRENT_DATE,350,290,0,8,6,20,320);

INSERT IGNORE INTO library_items (user_id,story_id) VALUES
(@user1,@story2),(@user2,@story1),(@user3,@story1),(@user4,@story2);

INSERT IGNORE INTO story_follows (user_id,story_id,notify_new_chapter) VALUES
(@user1,@story2,TRUE),(@user2,@story1,TRUE),(@user3,@story1,FALSE),(@user4,@story2,TRUE);

-- ---------------------------------------------------------------------------
-- COMMENTS / REPORTS
-- ---------------------------------------------------------------------------
SET @comment1 = UUID_TO_BIN('60000000-0000-0000-0000-000000000001');
SET @comment2 = UUID_TO_BIN('60000000-0000-0000-0000-000000000002');
SET @comment3 = UUID_TO_BIN('60000000-0000-0000-0000-000000000003');
INSERT IGNORE INTO comments (id,user_id,story_id,chapter_id,parent_id,content,status,like_count_cache) VALUES
(@comment1,@user2,@story1,@chap11,NULL,'Chương mở đầu khá cuốn.','VISIBLE',2),
(@comment2,@user3,@story1,@chap11,@comment1,'Đồng ý, nhịp truyện ổn.','VISIBLE',1),
(@comment3,@user1,@story2,@chap21,NULL,'Mong team ra chương mới sớm.','VISIBLE',1);

INSERT IGNORE INTO comment_likes (comment_id,user_id) VALUES
(@comment1,@user1),(@comment1,@user3),(@comment2,@user2),(@comment3,@user4);

INSERT IGNORE INTO reports
(id,reporter_id,target_type,target_id,report_type,description,status,handled_by,admin_note,resolved_at)
VALUES
(UUID_TO_BIN('61000000-0000-0000-0000-000000000001'),@user4,'COMMENT',@comment2,'SPAM','Báo cáo mẫu để test hàng chờ.','OPEN',NULL,NULL,NULL),
(UUID_TO_BIN('61000000-0000-0000-0000-000000000002'),@user2,'STORY',@story2,'OTHER','Báo cáo đã xử lý mẫu.','RESOLVED',@admin,'Không phát hiện vi phạm.',NOW(3));

-- ---------------------------------------------------------------------------
-- CURRENCY / WALLET
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO currencies(code,name) VALUES ('COIN','Xu'),('GEM','Ngọc');

INSERT IGNORE INTO wallets (id,user_id,coin_balance,gem_balance) VALUES
(UUID_TO_BIN('70000000-0000-0000-0000-000000000001'),@admin,1000000,100000),
(UUID_TO_BIN('70000000-0000-0000-0000-000000000002'),@user1,5000,500),
(UUID_TO_BIN('70000000-0000-0000-0000-000000000003'),@user2,3200,280),
(UUID_TO_BIN('70000000-0000-0000-0000-000000000004'),@user3,1800,120),
(UUID_TO_BIN('70000000-0000-0000-0000-000000000005'),@user4,900,50);

INSERT IGNORE INTO wallet_transactions
(id,user_id,currency,type,amount,balance_after,reference_type,reference_id,idempotency_key,description)
VALUES
(UUID_TO_BIN('71000000-0000-0000-0000-000000000001'),@user2,'COIN','DEPOSIT',5000,5000,'PAYMENT',NULL,'seed-wallet-001','Nạp xu mẫu'),
(UUID_TO_BIN('71000000-0000-0000-0000-000000000002'),@user2,'COIN','PURCHASE',-100,4900,'CHAPTER',@chap12,'seed-wallet-002','Mua chương 2'),
(UUID_TO_BIN('71000000-0000-0000-0000-000000000003'),@user3,'GEM','RECOMMENDATION',-30,120,'STORY',@story1,'seed-wallet-003','Đề cử truyện'),
(UUID_TO_BIN('71000000-0000-0000-0000-000000000004'),@user4,'COIN','DAILY_REWARD',100,900,'MISSION',NULL,'seed-wallet-004','Thưởng nhiệm vụ');

-- ---------------------------------------------------------------------------
-- PAYMENT
-- ---------------------------------------------------------------------------
SET @paymethod1 = UUID_TO_BIN('72000000-0000-0000-0000-000000000001');
SET @paymethod2 = UUID_TO_BIN('72000000-0000-0000-0000-000000000002');
INSERT IGNORE INTO payment_methods (id,name,type,config,instructions,is_active,sort_order,created_by) VALUES
(@paymethod1,'Chuyển khoản ngân hàng','BANK_TRANSFER',JSON_OBJECT('bank','VCB','accountName','GIOITRUYEN TEST'),'Nội dung: mã giao dịch của bạn.',TRUE,1,@admin),
(@paymethod2,'QR thanh toán','QR',JSON_OBJECT('provider','SEED_QR'),'Quét QR trong môi trường test.',TRUE,2,@admin);

SET @pkg1 = UUID_TO_BIN('73000000-0000-0000-0000-000000000001');
SET @pkg2 = UUID_TO_BIN('73000000-0000-0000-0000-000000000002');
SET @pkg3 = UUID_TO_BIN('73000000-0000-0000-0000-000000000003');
INSERT IGNORE INTO deposit_packages
(id,name,price_vnd,coin_amount,gem_amount,bonus_coin,bonus_gem,is_active)
VALUES
(@pkg1,'Gói 20K',20000,2000,20,0,0,TRUE),
(@pkg2,'Gói 50K',50000,5200,60,200,10,TRUE),
(@pkg3,'Gói 100K',100000,11000,130,1000,30,TRUE);

SET @payment1 = UUID_TO_BIN('74000000-0000-0000-0000-000000000001');
SET @payment2 = UUID_TO_BIN('74000000-0000-0000-0000-000000000002');
INSERT IGNORE INTO payments
(id,user_id,payment_method_id,deposit_package_id,amount_vnd,coin_received,gem_received,transaction_code,external_transaction_id,status,paid_at)
VALUES
(@payment1,@user2,@paymethod1,@pkg2,50000,5400,70,'SEED-PAY-001','SEED-EXT-001','PAID',DATE_SUB(NOW(3),INTERVAL 2 DAY)),
(@payment2,@user3,@paymethod2,@pkg1,20000,0,0,'SEED-PAY-002','SEED-EXT-002','PENDING',NULL);

-- ---------------------------------------------------------------------------
-- PURCHASE / UNLOCK
-- ---------------------------------------------------------------------------
SET @order1 = UUID_TO_BIN('75000000-0000-0000-0000-000000000001');
SET @order2 = UUID_TO_BIN('75000000-0000-0000-0000-000000000002');
INSERT IGNORE INTO purchase_orders (id,user_id,story_id,purchase_type,total_coin) VALUES
(@order1,@user2,@story1,'SINGLE_CHAPTER',100),
(@order2,@user1,@story2,'SINGLE_CHAPTER',80);

INSERT IGNORE INTO purchase_order_items (order_id,chapter_id,coin_price) VALUES
(@order1,@chap12,100),(@order2,@chap22,80);

INSERT IGNORE INTO chapter_unlocks (id,user_id,chapter_id,purchase_order_id,coin_paid) VALUES
(UUID_TO_BIN('76000000-0000-0000-0000-000000000001'),@user2,@chap12,@order1,100),
(UUID_TO_BIN('76000000-0000-0000-0000-000000000002'),@user1,@chap22,@order2,80);

-- ---------------------------------------------------------------------------
-- DONATION / TEAM LEDGER
-- ---------------------------------------------------------------------------
SET @donation1 = UUID_TO_BIN('77000000-0000-0000-0000-000000000001');
SET @donation2 = UUID_TO_BIN('77000000-0000-0000-0000-000000000002');
INSERT IGNORE INTO donations
(id,user_id,team_id,story_id,gross_coin,commission_rate,commission_coin,team_net_coin,message)
VALUES
(@donation1,@user2,@team1,@story1,1000,10.00,100,900,'Cảm ơn team!'),
(@donation2,@user1,@team2,@story3,400,10.00,40,360,'Ủng hộ truyện mới.');

INSERT IGNORE INTO team_ledger
(id,team_id,type,gross_coin,platform_fee_coin,net_coin,reference_type,reference_id)
VALUES
(UUID_TO_BIN('78000000-0000-0000-0000-000000000001'),@team1,'DONATION',1000,100,900,'DONATION',@donation1),
(UUID_TO_BIN('78000000-0000-0000-0000-000000000002'),@team2,'DONATION',400,40,360,'DONATION',@donation2),
(UUID_TO_BIN('78000000-0000-0000-0000-000000000003'),@team1,'STORY_PURCHASE',100,10,90,'PURCHASE_ORDER',@order1);

-- ---------------------------------------------------------------------------
-- RECOMMENDATION / RANKING
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO story_recommendations (id,user_id,story_id,gem_amount) VALUES
(UUID_TO_BIN('79000000-0000-0000-0000-000000000001'),@user3,@story1,30),
(UUID_TO_BIN('79000000-0000-0000-0000-000000000002'),@user4,@story1,20),
(UUID_TO_BIN('79000000-0000-0000-0000-000000000003'),@user1,@story2,15);

INSERT IGNORE INTO ranking_snapshots
(id,ranking_type,story_id,score,rank_position,period,snapshot_date)
VALUES
(UUID_TO_BIN('7a000000-0000-0000-0000-000000000001'),'VIEWS',@story1,8500,1,'DAILY',CURRENT_DATE),
(UUID_TO_BIN('7a000000-0000-0000-0000-000000000002'),'VIEWS',@story2,4000,2,'DAILY',CURRENT_DATE),
(UUID_TO_BIN('7a000000-0000-0000-0000-000000000003'),'GEM_RECOMMENDATION',@story1,900,1,'WEEKLY',CURRENT_DATE),
(UUID_TO_BIN('7a000000-0000-0000-0000-000000000004'),'GEM_RECOMMENDATION',@story2,350,2,'WEEKLY',CURRENT_DATE);

-- ---------------------------------------------------------------------------
-- NOTIFICATIONS
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO notifications
(id,user_id,type,title,message,target_type,target_id,target_url,is_read)
VALUES
(UUID_TO_BIN('80000000-0000-0000-0000-000000000001'),@user2,'NEW_CHAPTER','Có chương mới','Kiếm Khách Thiên Hà vừa có chương mới.','STORY',@story1,'/stories/kiem-khach-thien-ha',FALSE),
(UUID_TO_BIN('80000000-0000-0000-0000-000000000002'),@user1,'PURCHASE','Mua chương thành công','Bạn đã mở khóa một chương.','CHAPTER',@chap22,NULL,TRUE),
(UUID_TO_BIN('80000000-0000-0000-0000-000000000003'),@user3,'SYSTEM','Chào mừng','Chào mừng bạn đến với Giới Truyện.',NULL,NULL,NULL,FALSE);

INSERT IGNORE INTO notification_preferences
(user_id,story_updates,team_updates,system_updates,payment_updates)
VALUES
(@user1,TRUE,TRUE,TRUE,TRUE),
(@user2,TRUE,FALSE,TRUE,TRUE),
(@user3,TRUE,TRUE,TRUE,FALSE),
(@user4,FALSE,TRUE,TRUE,TRUE);

-- ---------------------------------------------------------------------------
-- MISSIONS
-- ---------------------------------------------------------------------------
SET @mission1 = UUID_TO_BIN('81000000-0000-0000-0000-000000000001');
SET @mission2 = UUID_TO_BIN('81000000-0000-0000-0000-000000000002');
SET @mission3 = UUID_TO_BIN('81000000-0000-0000-0000-000000000003');
INSERT IGNORE INTO missions
(id,code,name,description,mission_type,target_count,reward_coin,reward_gem,daily_limit,is_active)
VALUES
(@mission1,'DAILY_LOGIN','Đăng nhập mỗi ngày','Đăng nhập một lần trong ngày.','LOGIN',1,50,0,1,TRUE),
(@mission2,'READ_3_CHAPTERS','Đọc 3 chương','Đọc ba chương trong ngày.','READ_CHAPTER',3,100,2,1,TRUE),
(@mission3,'COMMENT_ONCE','Bình luận','Đăng một bình luận hợp lệ.','COMMENT',1,30,0,1,TRUE);

INSERT IGNORE INTO user_mission_progress
(user_id,mission_id,progress_date,progress,completed,claimed,claimed_at)
VALUES
(@user1,@mission1,CURRENT_DATE,1,TRUE,TRUE,NOW(3)),
(@user2,@mission2,CURRENT_DATE,2,FALSE,FALSE,NULL),
(@user3,@mission3,CURRENT_DATE,1,TRUE,FALSE,NULL),
(@user4,@mission1,CURRENT_DATE,1,TRUE,TRUE,NOW(3));

-- ---------------------------------------------------------------------------
-- REFERRAL
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO referral_codes (id,user_id,code) VALUES
(UUID_TO_BIN('82000000-0000-0000-0000-000000000001'),@user1,'MINH-SEED'),
(UUID_TO_BIN('82000000-0000-0000-0000-000000000002'),@user2,'LAN-SEED'),
(UUID_TO_BIN('82000000-0000-0000-0000-000000000003'),@user3,'HUY-SEED');

INSERT IGNORE INTO referrals
(id,referrer_id,referred_user_id,status,qualified_at,reward_coin,reward_gem,rewarded_at)
VALUES
(UUID_TO_BIN('83000000-0000-0000-0000-000000000001'),@user1,@user4,'REWARDED',DATE_SUB(NOW(3),INTERVAL 3 DAY),200,5,DATE_SUB(NOW(3),INTERVAL 2 DAY)),
(UUID_TO_BIN('83000000-0000-0000-0000-000000000002'),@user2,@user3,'QUALIFIED',DATE_SUB(NOW(3),INTERVAL 1 DAY),200,5,NULL);

-- ---------------------------------------------------------------------------
-- COMMUNITY
-- ---------------------------------------------------------------------------
SET @room1 = UUID_TO_BIN('84000000-0000-0000-0000-000000000001');
SET @room2 = UUID_TO_BIN('84000000-0000-0000-0000-000000000002');
INSERT IGNORE INTO community_rooms (id,name,status) VALUES
(@room1,'Thảo luận chung','VISIBLE'),
(@room2,'Góc đề cử truyện','VISIBLE');

SET @msg1 = UUID_TO_BIN('85000000-0000-0000-0000-000000000001');
SET @msg2 = UUID_TO_BIN('85000000-0000-0000-0000-000000000002');
INSERT IGNORE INTO community_messages (id,room_id,user_id,content,reply_to_id,status) VALUES
(@msg1,@room1,@user1,'Mọi người đang đọc truyện nào?',NULL,'VISIBLE'),
(@msg2,@room1,@user2,'Mình đang đọc Kiếm Khách Thiên Hà.',@msg1,'VISIBLE'),
(UUID_TO_BIN('85000000-0000-0000-0000-000000000003'),@room2,@user3,'Đề cử một bộ trinh thám hay nhé.',NULL,'VISIBLE');

-- ---------------------------------------------------------------------------
-- SITE SETTINGS / DOCUMENTS
-- ---------------------------------------------------------------------------
INSERT INTO site_settings(setting_key,value,updated_by) VALUES
('donation_commission_percent',JSON_OBJECT('value',10),@admin),
('registration_enabled',JSON_OBJECT('value',TRUE),@admin),
('maintenance_mode',JSON_OBJECT('value',FALSE),@admin),
('daily_free_coin_limit',JSON_OBJECT('value',1000),@admin),
('default_theme',JSON_OBJECT('value','SYSTEM'),@admin),
('referral_reward',JSON_OBJECT('coin',200,'gem',5),@admin)
ON DUPLICATE KEY UPDATE value=VALUES(value), updated_by=VALUES(updated_by);

INSERT IGNORE INTO site_documents
(id,type,title,content,version,is_published,created_by,published_at)
VALUES
(UUID_TO_BIN('86000000-0000-0000-0000-000000000001'),'TERMS','Điều khoản sử dụng','Nội dung điều khoản mẫu cho môi trường development.',1,TRUE,@admin,NOW(3)),
(UUID_TO_BIN('86000000-0000-0000-0000-000000000002'),'PRIVACY','Chính sách riêng tư','Nội dung chính sách riêng tư mẫu.',1,TRUE,@admin,NOW(3)),
(UUID_TO_BIN('86000000-0000-0000-0000-000000000003'),'TEAM_RULES','Quy định Team','Nội dung quy định đăng và quản lý truyện mẫu.',1,TRUE,@admin,NOW(3));

-- ---------------------------------------------------------------------------
-- ADVERTISEMENTS
-- ---------------------------------------------------------------------------
SET @ad1 = UUID_TO_BIN('87000000-0000-0000-0000-000000000001');
SET @ad2 = UUID_TO_BIN('87000000-0000-0000-0000-000000000002');
SET @ad3 = UUID_TO_BIN('87000000-0000-0000-0000-000000000003');
INSERT IGNORE INTO advertisements
(id,name,type,image_url,target_url,placement,cooldown_seconds,max_clicks_per_day,priority,start_at,end_at,is_active)
VALUES
(@ad1,'Global Affiliate Seed','AFFILIATE_REDIRECT',NULL,'https://example.com/affiliate/global','GLOBAL_CLICK',600,5,100,DATE_SUB(NOW(3),INTERVAL 1 DAY),DATE_ADD(NOW(3),INTERVAL 30 DAY),TRUE),
(@ad2,'Home Banner Seed','BANNER','/seed/ads/banner-home.webp','https://example.com/promo/home','HOME',0,10,50,NULL,NULL,TRUE),
(@ad3,'Reader Campaign Disabled','AFFILIATE_REDIRECT',NULL,'https://example.com/affiliate/reader','READER',900,3,20,NULL,NULL,FALSE);

INSERT IGNORE INTO ad_events
(id,advertisement_id,user_id,session_id,story_id,page_url,ip_hash,event_type)
VALUES
(UUID_TO_BIN('88000000-0000-0000-0000-000000000001'),@ad1,@user2,'seed-session-001',@story1,'/stories/kiem-khach-thien-ha','seed-ip-1','CLICK'),
(UUID_TO_BIN('88000000-0000-0000-0000-000000000002'),@ad1,@user3,'seed-session-002',@story1,'/stories/kiem-khach-thien-ha/chapter-1','seed-ip-2','REDIRECT'),
(UUID_TO_BIN('88000000-0000-0000-0000-000000000003'),@ad2,@user1,'seed-session-004',@story2,'/','seed-ip-3','IMPRESSION');

-- ---------------------------------------------------------------------------
-- ADMIN AUDIT
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO admin_audit_logs
(id,admin_id,action,entity_type,entity_id,old_data,new_data,ip_hash)
VALUES
(UUID_TO_BIN('89000000-0000-0000-0000-000000000001'),@admin,'CREATE_TEAM','TEAM',@team1,NULL,JSON_OBJECT('name','Thiên Hà Team'),'seed-admin-ip'),
(UUID_TO_BIN('89000000-0000-0000-0000-000000000002'),@admin,'APPROVE_STORY','STORY',@story1,JSON_OBJECT('status','PENDING_REVIEW'),JSON_OBJECT('status','PUBLISHED'),'seed-admin-ip'),
(UUID_TO_BIN('89000000-0000-0000-0000-000000000003'),@admin,'CREATE_ADVERTISEMENT','ADVERTISEMENT',@ad1,NULL,JSON_OBJECT('placement','GLOBAL_CLICK','active',TRUE),'seed-admin-ip');

-- ---------------------------------------------------------------------------
-- Sanity summary
-- ---------------------------------------------------------------------------
SELECT 'Seed completed' AS status;
