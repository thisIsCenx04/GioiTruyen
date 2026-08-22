# Sơ đồ thực thể

65 bảng. Vẽ tất cả vào một sơ đồ thì không ai đọc được, nên chia theo miền nghiệp vụ —
đúng theo cách các module backend chia.

Chỉ vẽ cột khoá và cột mang ý nghĩa nghiệp vụ; cột `created_at` / `updated_at` có ở hầu
hết bảng nên bỏ qua cho gọn.

---

## 1. Nội dung

Xương sống của cả nền tảng.

![1. Nội dung](images/entities-1-1-noi-dung.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
erDiagram
    teams ||--o{ stories : "sở hữu"
    stories ||--o{ chapters : "gồm"
    stories }o--o{ genres : "story_genres"
    stories ||--o{ story_tags : "gắn nhãn"
    chapters ||--o| chapter_audios : "bản audio"
    users ||--o{ stories : "created_by"

    teams {
        varchar id PK
        varchar slug UK
        varchar name
        enum status "ACTIVE|PENDING_REVIEW|SUSPENDED"
        varchar created_by FK
        bigint promotion_discount_coin "admin-only, không lộ cho nhóm"
    }

    stories {
        varchar id PK
        varchar team_id FK
        varchar slug UK "giữ nguyên khi đổi tên truyện"
        varchar title
        varchar short_description "VARCHAR(500) — teaser cho card"
        mediumtext description "giới thiệu đầy đủ, trần 100.000 ký tự"
        enum status "DRAFT|PENDING_REVIEW|PUBLISHED|REJECTED|HIDDEN"
        enum progress_status "ONGOING|COMPLETED|PAUSED"
        enum story_format "SERIAL|ONESHOT"
        bigint combo_price_xu "null = không bán combo"
        bigint view_count_cache
        bigint follow_count_cache
        bigint favorite_count_cache
    }

    chapters {
        varchar id PK
        varchar story_id FK
        decimal chapter_number "UNIQUE cùng story_id — đánh theo vị trí khi lưu"
        varchar title "số chương của file nằm ở đây"
        varchar slug "UNIQUE cùng story_id"
        mediumtext content
        enum access_type "FREE|PAID"
        bigint coin_price "PAID mà = 0 thì bị từ chối"
    }
```

</details>

> **Điểm dễ nhầm.** `chapters.chapter_number` được đánh lại **theo vị trí** mỗi lần lưu,
> nên luôn là 1…N liền mạch. Số chương mà file gốc ghi nằm trong `title`. Truyện nhập từ
> file thiếu chương 87 sẽ có 385 dòng đánh số 1–385, nhưng tiêu đề chạy tới "Chương 386".
> Cảnh báo thiếu chương phải đọc từ **title**, không đọc từ `chapter_number`.

---

## 2. Đọc và tương tác

![2. Đọc và tương tác](images/entities-2-2-doc-va-tuong-tac.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
erDiagram
    users ||--o{ reading_progress : "đang đọc"
    users ||--o{ library_items : "tủ sách"
    users ||--o{ story_follows : "theo dõi"
    users ||--o{ comments : "bình luận"
    users ||--o{ story_views : "lượt xem"
    comments ||--o{ comment_likes : "thả tim"
    comments ||--o{ comments : "trả lời"
    stories ||--o{ story_daily_stats : "thống kê ngày"

    reading_progress {
        varchar user_id FK
        varchar story_id FK
        varchar chapter_id FK
        int progress_percent
    }
    library_items {
        varchar user_id FK
        varchar story_id FK
    }
    comments {
        varchar id PK
        varchar story_id FK
        varchar chapter_id FK "null = bình luận truyện"
        varchar parent_id FK
        enum status "xoá là đổi trạng thái, không xoá dòng"
    }
    story_views {
        varchar id PK
        varchar story_id FK
        timestamp viewed_at "ghi sau 3 giây đọc, không ghi khi mở"
    }
```

</details>

Bình luận bị xoá **giữ nguyên dòng** và đổi `status`. Một chuỗi trả lời bị thủng ở giữa
thì đọc như hỏng, và kiểm duyệt cần thấy nội dung đã nói gì.

---

## 3. Tiền

Miền phức tạp nhất, và là nơi một lỗi nhỏ thành mất tiền thật.

![3. Tiền](images/entities-3-3-tien.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
erDiagram
    users ||--|| wallets : "một ví"
    users ||--o{ wallet_transactions : "sổ giao dịch"
    users ||--o{ chapter_unlocks : "đã mở khoá"
    users ||--o{ story_combo_purchases : "mua combo"
    users ||--o{ donations : "ủng hộ"
    users ||--o{ payments : "nạp Xu"
    users ||--o{ withdrawal_requests : "rút tiền"
    teams ||--o{ team_ledger : "sổ thu nhập nhóm"
    chapters ||--o{ chapter_unlocks : "được mở"

    wallets {
        varchar user_id UK
        bigint coin_balance "CHECK >= 0"
        bigint gem_balance
    }
    wallet_transactions {
        varchar id PK
        enum type "DEPOSIT|PURCHASE|EARNING|REFUND|WITHDRAWAL|PR_ESCROW|PR_REWARD|…"
        bigint amount "âm = trừ"
        bigint balance_after "số dư sau — cho phép dò lại lịch sử"
        varchar reference_type
        varchar reference_id
    }
    chapter_unlocks {
        varchar id PK
        varchar user_id FK
        varchar chapter_id FK
        bigint coin_paid
    }
    donations {
        varchar id PK
        bigint gross_coin
        decimal commission_rate
        bigint commission_coin
        bigint team_net_coin
    }
    team_ledger {
        varchar id PK
        enum type "DONATION|CHAPTER_UNLOCK|WITHDRAWAL|PLATFORM_FEE"
        bigint gross_coin
        bigint platform_fee_coin
        bigint net_coin "CHECK >= 0 — không ghi được số âm"
    }
```

</details>

Ba điều cần nhớ:

1. **Không có ví nhóm.** Thu nhập của nhóm vào ví của chủ nhóm. `team_ledger` là sổ đối
   chiếu, không phải nơi giữ tiền.
2. `wallet_transactions.balance_after` cho phép dựng lại số dư tại bất kỳ thời điểm nào —
   nếu số dư lệch, đây là chỗ tìm ra lệch từ đâu.
3. `team_ledger.net_coin` có `CHECK >= 0`, nên **không ghi được khoản chi**. Chi tiêu của
   nhóm (bố cáo, chiến dịch PR) chỉ để lại dấu ở `wallet_transactions`.

---

## 4. Quảng bá

Hai cơ chế khác hẳn nhau, thường bị nhầm là một.

![4. Quảng bá](images/entities-4-4-quang-ba.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
erDiagram
    teams ||--o{ story_promotions : "mua bố cáo"
    promotion_packages ||--o{ story_promotions : "theo gói"
    teams ||--o{ pr_quests : "đăng nhiệm vụ PR"
    pr_quests ||--o{ pr_quest_claims : "người nhận"
    pr_quest_claims ||--o{ pr_claim_files : "ảnh chụp"
    pr_quest_claims ||--o{ pr_disputes : "khiếu nại"
    users ||--o{ pr_quest_claims : "nhận việc"

    story_promotions {
        varchar id PK
        varchar story_id FK
        int duration_days
        bigint coin_paid
        enum status "ACTIVE|EXPIRED|CANCELLED"
        int slot_position
    }
    pr_quests {
        varchar id PK
        enum quest_kind "OPEN|APPLY"
        enum platform "TIKTOK|YOUTUBE|FACEBOOK|OTHER"
        text requirement "KPI viết bằng lời"
        bigint reward_xu "mỗi suất"
        int slot_count
        int claimed_count "chỉ tăng bằng UPDATE có điều kiện"
        bigint escrow_xu "ký quỹ, rút dần mỗi lần duyệt"
        bigint publish_fee_xu "10.000, phí duy nhất, không bao giờ hoàn"
        enum status "DRAFT|OPEN|FULL|CLOSED|CANCELLED"
    }
    pr_quest_claims {
        varchar id PK
        enum status "PENDING|DECLINED|CLAIMED|SUBMITTED|APPROVED|REJECTED|EXPIRED"
        timestamp submit_due_at "7 ngày — quá hạn trả suất"
        timestamp review_due_at "7 ngày — quá hạn tự duyệt và trả tiền"
        boolean auto_approved
        varchar submission_url
    }
    pr_disputes {
        varchar id PK
        enum raised_role "CREATOR|TEAM"
        bigint penalty_xu "admin chuyển Xu giữa hai bên"
    }
```

</details>

| | Bố cáo | Chiến dịch PR |
|---|---|---|
| Mua cái gì | **Vị trí** hiển thị trên trang chủ | **Kết quả** — người thật làm nội dung |
| Diễn ra ở đâu | Trên web | Ngoài web: TikTok, YouTube, Facebook |
| Trả tiền cho ai | Nền tảng | Độc giả làm nội dung |
| Kiểm chứng | Tự động, theo ngày | Người duyệt |

`UNIQUE (quest_id, user_id)` trên `pr_quest_claims` là thứ khiến một người không thể
nhận hai lần, kể cả khi bấm hai tab cùng lúc.

---

## 5. Nhiệm vụ và game hoá

![5. Nhiệm vụ và game hoá](images/entities-5-5-nhiem-vu-va-game-hoa.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
erDiagram
    quest_definitions ||--o{ user_quest_progress : "tiến độ theo ngày"
    missions ||--o{ user_mission_progress : "tiến độ theo ngày"
    users ||--o{ referral_codes : "mã giới thiệu"
    referral_codes ||--o{ referrals : "đã dùng"

    quest_definitions {
        varchar id PK
        enum quest_type "LOGIN_DAILY|READ_MINUTES|READ_CHAPTERS|SHARE_STORY|…"
        int target_value
        int reward_coin
    }
    user_quest_progress {
        varchar user_id FK
        varchar quest_id FK
        date quest_date "UNIQUE cùng user+quest — chặn nhận thưởng hai lần"
    }
```

</details>

`quest_definitions` là nhiệm vụ **lặp mỗi ngày**, có `daily_limit`. Chiến dịch PR thì
gắn một truyện, có ngân sách, mỗi người làm một lần — nên dùng bảng riêng chứ không ghép
vào đây. Ghép vào sẽ làm hỏng ý nghĩa của nhiệm vụ ngày.

---

## 6. Hệ thống và kiểm duyệt

![6. Hệ thống và kiểm duyệt](images/entities-6-6-he-thong-va-kiem-duyet.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
erDiagram
    advertisements ||--o{ ad_events : "hiển thị và click"
    users ||--o{ reports : "báo cáo vi phạm"
    users ||--o{ notifications : "thông báo"
    users ||--o{ admin_audit_logs : "hành động admin"
    outbox_messages }o--|| inbox_receipts : "giao nhận sự kiện"

    advertisements {
        varchar id PK
        enum type "BANNER|POPUP|AFFILIATE_REDIRECT"
        enum placement "GLOBAL_CLICK|STORY_DETAIL|READER|HOME|SIDEBAR"
        int cooldown_seconds
        int max_clicks_per_day
    }
    ad_events {
        varchar id PK
        enum event_type "IMPRESSION|CLICK|REDIRECT"
        text ip_hash "băm, không lưu IP thô"
    }
    admin_audit_logs {
        varchar id PK
        varchar action
        json old_data
        json new_data
    }
```

</details>

`ad_events` chỉ ghi banner **của hệ thống**. Số liệu AdSense không nằm trong CSDL này —
chúng ở tài khoản AdSense và cần AdSense Management API mới đọc được.
