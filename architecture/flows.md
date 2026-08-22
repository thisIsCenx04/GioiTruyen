# Luồng nghiệp vụ

Bốn luồng quan trọng nhất, và với mỗi luồng là chỗ nó **từng hỏng**.

---

## 1. Mở khoá chương trả phí

![1. Mở khoá chương trả phí](images/flows-1-1-mo-khoa-chuong-tra-phi.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
sequenceDiagram
    participant R as Độc giả
    participant API as ChapterController
    participant W as Ví (wallets)
    participant U as chapter_unlocks
    participant L as team_ledger

    R->>API: POST /chapters/{id}/unlock
    API->>U: đã mở khoá chưa?
    alt Đã mở rồi
        API-->>R: 200 — không tính tiền lần hai
    else Chưa
        API->>W: SELECT coin_balance FOR UPDATE
        Note over W: Khoá dòng — hai lần mua<br/>đồng thời không rút quá số dư
        alt Không đủ Xu
            API-->>R: 409 kèm số cần và số còn
        else Đủ
            API->>W: trừ + ghi wallet_transactions
            API->>U: INSERT chapter_unlocks
            API->>L: ghi thu nhập nhóm (đã trừ hoa hồng)
            API-->>R: 200
        end
    end
```

</details>

Toàn bộ nằm trong một transaction. `FOR UPDATE` là thứ khiến hai tab mua cùng lúc không
tiêu quá số dư.

---

## 2. Đăng truyện từ file

Luồng phức tạp nhất phía client, và là nơi mất chữ dễ xảy ra nhất.

![2. Đăng truyện từ file](images/flows-2-2-dang-truyen-tu-file.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
flowchart TD
    A["Chọn file<br/>docx · epub · odt · rtf · html · txt · md"] --> B["Đọc theo magic byte<br/>không tin phần mở rộng"]
    B --> C["Tách thành DocumentLine<br/>{text, heading}"]
    C --> D["Đọc khối đầu file:<br/>Tên truyện · Tác giả · Thể loại · Văn án"]
    D --> E{"Có dòng chương thật?"}
    E -->|Có| F["Cắt tại đúng các dòng đó<br/>KHÔNG cắt thêm theo số từ"]
    E -->|Không| G["Cắt theo ngân sách từ<br/>800 · hoặc 1400 cho Zhihu"]
    F --> H["Đối chiếu số từ<br/>nguồn = header + tiêu đề + nội dung"]
    G --> H
    H --> I{"Số chương có nhảy cóc?"}
    I -->|Có| J["Cảnh báo: thiếu chương 87…"]
    I -->|Không| K["Dialog kết quả"]
    J --> K
```

</details>

### Ba cái bẫy đã cắn thật

| Triệu chứng | Nguyên nhân |
|---|---|
| 83 chương thay vì 72 | Dòng ngày `17.3.2015.` khớp luật "chương là số trần" |
| Hai chương 48.000 từ, MySQL từ chối | Một dòng `1` lạc và một dòng danh sách `3. Mua cùng…` bị đọc thành tiêu đề chương |
| File 101 chương thành **một** chương 78.310 từ | "Thêm file" đọc cả file thành một chương, không nhìn dòng phân chương bên trong |

Cái thứ ba đáng nhớ nhất: một file bổ sung như `Up-2 108-208.docx` **là 101 chương**,
không phải một chương. Nay dòng phân chương của chính file quyết định — nhiều dòng thì
nhiều chương, không dòng nào thì file đó thật sự là một chương và giữ nguyên toàn bộ.

### Lưu chương: đối chiếu theo id, không theo vị trí

![Lưu chương: đối chiếu theo id, không theo vị trí](images/flows-3-luu-chuong-doi-chieu-theo-id-khong-theo-vi-tri.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
flowchart LR
    A["Đọc chương hiện có<br/>theo id"] --> B["Đỗ mọi dòng lên số âm<br/>chapter_number = -n-1"]
    B --> C["Đổi slug thành tmp-{id}"]
    C --> D{"Draft có id?"}
    D -->|Có| E["UPDATE theo id"]
    D -->|Không| F["INSERT chương mới"]
    E --> G["Chương đã có người mua<br/>còn đỗ ở số âm → xếp lại cuối danh sách"]
    F --> G
```

</details>

Việc "đỗ lên số âm" là bắt buộc: `UNIQUE (story_id, chapter_number)` sẽ đụng nhau giữa
chừng nếu đánh số lại tại chỗ. Bản cũ xoá rồi chèn lại theo **vị trí**, nên chèn một
chương vào giữa là ghi đè nhầm chương và độc giả mất chương đã mua.

---

## 3. Chiến dịch PR — vòng đời

![3. Chiến dịch PR — vòng đời](images/flows-4-3-chien-dich-pr-vong-doi.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
stateDiagram-v2
    [*] --> DRAFT: tạo nháp
    DRAFT --> DRAFT: sửa, xoá tự do
    DRAFT --> OPEN: publish<br/>(trừ ví + ký quỹ)
    OPEN --> FULL: đủ người nhận
    FULL --> OPEN: một suất quá hạn nộp<br/>→ trả về kho
    OPEN --> CLOSED: hết hạn đăng ký
    OPEN --> CANCELLED: chủ nhóm dừng
    FULL --> CANCELLED: chủ nhóm dừng
    CLOSED --> [*]: hoàn Xu chưa dùng
    CANCELLED --> [*]: hoàn phần chưa ai nhận
```

</details>

Trạng thái của một suất:

![3. Chiến dịch PR — vòng đời](images/flows-5-3-chien-dich-pr-vong-doi.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
stateDiagram-v2
    [*] --> PENDING: gửi đơn (quest APPLY)
    [*] --> CLAIMED: nhận ngay (quest OPEN)
    PENDING --> CLAIMED: nhóm chọn → chiếm suất
    PENDING --> DECLINED: nhóm không chọn
    CLAIMED --> SUBMITTED: nộp link
    CLAIMED --> EXPIRED: quá 7 ngày không nộp<br/>→ trả suất
    SUBMITTED --> APPROVED: nhóm duyệt → trả Xu
    SUBMITTED --> APPROVED: quá 7 ngày<br/>→ TỰ ĐỘNG duyệt và trả
    SUBMITTED --> REJECTED: từ chối kèm lý do
    REJECTED --> SUBMITTED: sửa và nộp lại
```

</details>

### Chiếm suất khi nhiều người bấm cùng lúc

Cách **sai** — có khe hở giữa đọc và ghi:

```sql
SELECT claimed_count FROM pr_quests WHERE id = ?;   -- cả hai đọc thấy 4/5
INSERT INTO pr_quest_claims ...                     -- cả hai cùng ghi → 6/5
```

Cách **đúng** — mọi điều kiện nằm trong `WHERE`, một câu, tự nguyên tử:

```sql
UPDATE pr_quests
   SET claimed_count = claimed_count + 1
 WHERE id = :questId
   AND status = 'OPEN'
   AND claimed_count < slot_count
   AND registration_ends_at > NOW(3);
```

`1` dòng = giành được suất. `0` = hết suất hoặc quá hạn. MySQL khoá dòng trong lúc UPDATE
nên hai request buộc xếp hàng — không cần khoá thủ công, không cần hàng đợi.

Thêm lớp thứ hai: `UNIQUE (quest_id, user_id)`.

### Và thứ tự hai câu đó cũng quan trọng

Test tích hợp phát hiện: **INSERT phải chạy trước UPDATE**.

Đặt `takeSlot()` trước có nghĩa là cú bấm trùng — thứ mà `UNIQUE` rồi sẽ chặn — **đã tiêu
mất một suất**. Transaction rollback cứu được, nhưng chỉ vì exception thoát ra ngoài; ai
đó về sau bắt và nuốt nó là suất bay âm thầm. Đảo lại thì cú bấm trùng không thể bị tính
tiền, bất kể chuyện gì xảy ra sau đó.

### Tiền chảy đi đâu

![Tiền chảy đi đâu](images/flows-6-tien-chay-di-dau.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
flowchart LR
    W["Ví chủ nhóm"] -->|"publish"| E["escrow_xu<br/>300.000"]
    W -->|"publish"| F["fee_reserved_xu<br/>30.000"]
    W -->|"publish"| P["publish_fee_xu<br/>15.000<br/>KHÔNG BAO GIỜ HOÀN"]
    E -->|"mỗi lần duyệt"| C["Ví creator<br/>60.000"]
    F -->|"mỗi lần duyệt"| PLAT["Nền tảng<br/>6.000"]
    E -->|"đóng chiến dịch"| W2["Hoàn về ví"]
    F -->|"đóng chiến dịch"| W2
```

</details>

Với 5 suất, chỉ 1 người được duyệt: trừ **345.000**, trả creator **60.000**, nền tảng giữ
**15.000 + 6.000**, hoàn lại **264.000**. Đã kiểm chứng bằng test chạy tiền thật trong
CSDL.

Phí publish không hoàn là thứ khiến việc "tự thuê tài khoản phụ của mình" mất tiền chứ
không được lợi.

---

## 4. Việc chạy theo giờ

![4. Việc chạy theo giờ](images/flows-7-4-viec-chay-theo-gio.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
flowchart TD
    T["PrQuestScheduler<br/>15 phút / lần"] --> A["Suất quá hạn nộp<br/>→ EXPIRED, trả suất, FULL→OPEN"]
    T --> B["Bài quá hạn duyệt<br/>→ TỰ DUYỆT, trả Xu"]
    T --> C["Quest hết hạn đăng ký<br/>và không còn ai đang làm<br/>→ đóng, hoàn Xu"]
```

</details>

Không có lớp này thì mọi thời hạn chỉ là chữ trên màn hình. Chúng không thể chạy theo
request được — **người đang câu giờ chính là người không gửi request nào**.

Lỗi được nuốt và ghi log: lần chạy sau nhặt lại việc lần này bỏ sót, còn để exception
thoát ra chỉ khiến scheduler ngừng thử lại.
