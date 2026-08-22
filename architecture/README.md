# Kiến trúc Giới Truyện

Tài liệu mô tả hệ thống **như nó đang chạy**, không phải như dự định. Mọi con số và
tên bảng trong đây đều lấy từ mã nguồn và cơ sở dữ liệu thật.

| Tài liệu | Nội dung |
|---|---|
| [modules.md](modules.md) | Bản đồ 16 module backend, cấu trúc frontend, luật phụ thuộc |
| [entities.md](entities.md) | Sơ đồ thực thể, 65 bảng chia theo miền nghiệp vụ |
| [flows.md](flows.md) | Sơ đồ tuần tự cho các luồng quan trọng |

Đặc tả tính năng nằm ở [../featured/](../featured/).

---

## 1. Tổng quan

Giới Truyện là nền tảng đọc truyện chữ và truyện audio tiếng Việt. Ba nhóm người dùng:

- **Độc giả** — đọc miễn phí, mua chương trả phí bằng Xu, nhận nhiệm vụ
- **Nhóm xuất bản** — đăng truyện, đặt giá chương, chạy chiến dịch quảng bá
- **Quản trị viên** — duyệt nội dung, xử lý nạp/rút, phân xử tranh chấp

![1. Tổng quan](images/README-1-1-tong-quan.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
graph TB
    subgraph client["Trình duyệt"]
        SPA["React 18 + Vite 5<br/>SPA, react-router v6<br/>66 component · 60 route"]
    end

    subgraph vps["VPS — 187.127.214.54"]
        NGINX["nginx<br/>TLS · reverse proxy<br/>index.html: no-store<br/>/assets: immutable 1 năm"]
        PM2["pm2 → gioitruyen-fe<br/>phục vụ dist tĩnh"]
        API["systemd → gioitruyen-backend<br/>Spring Boot 3.5 · Java 21<br/>38 controller"]
        DB[("MySQL 8<br/>65 bảng<br/>Flyway V1…V29")]
        FILES[["uploads/<br/>bìa · avatar · banner<br/>ảnh chứng minh PR"]]
    end

    subgraph ext["Bên ngoài"]
        ADS["Google AdSense"]
        BANK["Chuyển khoản ngân hàng<br/>(nạp Xu, duyệt tay)"]
    end

    SPA -->|"/api/v1/*"| NGINX
    SPA -->|"HTML, JS, CSS"| NGINX
    NGINX --> PM2
    NGINX --> API
    API --> DB
    API --> FILES
    SPA -.-> ADS
    BANK -.-> API
```

</details>

### Vì sao là SPA chứ không phải SSR

Toàn bộ nội dung render phía client. Điều này có một hệ quả thật cần biết:
**crawler chỉ thấy vỏ HTML rỗng**, không thấy nội dung truyện. Ảnh hưởng cả SEO lẫn
việc AdSense chọn quảng cáo theo ngữ cảnh. Đây là giới hạn kiến trúc đã biết, sửa được
bằng prerender hoặc SSR cho route truyện và chương — chưa làm.

---

## 2. Triển khai

Một script duy nhất: `upload_vps.ps1`.

![2. Triển khai](images/README-2-2-trien-khai.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
graph LR
    A["mvn package<br/>→ JAR 57 MB"] --> C
    B["vite build<br/>→ dist 4 MB"] --> C
    C["scp lên VPS"] --> D["systemctl restart<br/>gioitruyen-backend"]
    C --> E["pm2 restart<br/>gioitruyen-fe"]
    D --> F["Flyway tự chạy<br/>migration mới"]
    F --> G["Health check<br/>tối đa 90s"]
    E --> G
```

</details>

Migration nằm sẵn trong JAR (`classpath:db/migration`), Flyway chạy khi backend khởi động.
Script chỉ upload thêm bản `.sql` rời để đối chiếu tay.

**Ràng buộc đã biết**: SSH/SCP tới VPS chỉ xác thực được từ PowerShell, không từ Bash.

---

## 3. Những quyết định định hình hệ thống

| Quyết định | Lý do |
|---|---|
| Ví Xu là ví **người dùng**, không phải ví nhóm | Thu nhập nhóm ghi vào ví chủ nhóm; `team_ledger` chỉ là sổ đối chiếu |
| Mọi thao tác tiền dùng `FOR UPDATE` | Hai giao dịch đồng thời không được rút quá số dư |
| Chiếm suất/slot bằng **một câu UPDATE có điều kiện** | Không có khe hở giữa đọc và ghi — xem [flows.md](flows.md) |
| `chapters.content` là `MEDIUMTEXT` | `TEXT` là 65.535 **byte**; tiếng Việt ~3 byte/ký tự → trần ~21.800 ký tự |
| `index.html` gửi `no-store` | SPA không có cache header sẽ bị trình duyệt cache theo heuristic |
| Route không khai báo trong Security rơi vào `denyAll` | Trả **403**, không phải 404 — dễ hiểu nhầm là lỗi quyền |

---

## 4. Xác thực và phân quyền

![4. Xác thực và phân quyền](images/README-3-4-xac-thuc-va-phan-quyen.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
graph LR
    L["POST /login"] --> AT["Access token<br/>JWT · 30 phút"]
    L --> RT["Refresh token<br/>bảng refresh_tokens"]
    AT --> API["Spring Security<br/>OAuth2 Resource Server"]
    RT -->|"hết hạn → làm mới 1 lần"| AT
    API --> R1["SCOPE_ADMIN<br/>→ /admin/**"]
    API --> R2["Đăng nhập<br/>→ ví, tủ sách, nhiệm vụ"]
    API --> R3["Công khai<br/>→ đọc truyện, bảng nhiệm vụ"]
```

</details>

`users.role` chỉ phân biệt `READER` và `ADMIN`. Quyền xuất bản **không** nằm ở đó — nó
đến từ một dòng `team_members` đang `ACTIVE` với vai trò `OWNER` / `MANAGER` / `EDITOR` /
`MEMBER`. Tiêu tiền của nhóm chỉ `OWNER` và `MANAGER` được phép.
