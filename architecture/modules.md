# Bản đồ module

## Backend — 16 module

Monolith module hoá. Mỗi module là một package dưới `com.storyplatform`, có ranh giới
riêng, và ArchUnit giữ cho ranh giới đó không bị vượt.

![Backend — 16 module](images/modules-1-backend-16-module.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
graph TB
    subgraph edge["Cửa vào"]
        BOOT["bootstrap<br/>Security · CORS · cấu hình"]
        ADMIN["admin<br/>15 lớp<br/>CRUD quản trị, tải file"]
    end

    subgraph core["Nghiệp vụ"]
        CATALOG["catalog<br/>23 lớp<br/>truyện, chương, trang chủ"]
        TEAMS["teams<br/>13 lớp<br/>nhóm, thành viên, workspace"]
        MONET["monetization<br/>39 lớp<br/>ví, mở khoá, nạp, rút"]
        PROMO["promotion<br/>bố cáo + chiến dịch PR"]
        COMM["community<br/>12 lớp<br/>bình luận, chat"]
        ENGAGE["engagement<br/>15 lớp<br/>theo dõi, tủ sách, tiến độ"]
        GAME["gamification<br/>11 lớp"]
        QUEST["quest<br/>nhiệm vụ ngày"]
        MOD["moderation<br/>báo cáo vi phạm"]
        AUTHOR["author<br/>đăng ký làm nhóm"]
    end

    subgraph infra["Nền"]
        AUTH["auth<br/>29 lớp<br/>JWT, refresh, mật khẩu"]
        SHARED["shared<br/>48 lớp<br/>lỗi, sự kiện, cache, rate limit"]
        SYSTEM["system<br/>28 lớp<br/>quảng cáo, cấu hình, tài liệu"]
        MEDIA["media"]
    end

    ADMIN --> CATALOG
    ADMIN --> TEAMS
    TEAMS --> ADMIN
    PROMO --> MONET
    CATALOG --> MONET
    ENGAGE --> CATALOG
    COMM --> CATALOG
    QUEST --> GAME

    CATALOG --> SHARED
    MONET --> SHARED
    PROMO --> SHARED
    TEAMS --> SHARED
    AUTH --> SHARED
```

</details>

### Một phụ thuộc ngược cố ý

`teams` gọi vào `admin` — cụ thể là `TeamWorkspaceController` dùng lại
`AdminStoryController` để ghi truyện.

Nhìn thì ngược đời, nhưng lý do vững: **tạo truyện là cùng một việc dù ai yêu cầu**.
Luật slug, bảng liên kết thể loại và nhãn, đánh số chương, kiểm tra chương trả phí phải
có giá, và chốt chặn không cho mất chương — tất cả phải hành xử giống hệt nhau, và
khoảng 400 dòng logic. Nhân đôi sẽ thành hai bản trôi dạt khỏi nhau, mà bản của nhóm xuất
bản mới là bản không ai để ý là đã sai.

Cái mà `TeamWorkspaceController` thêm vào là **phân quyền**: chứng minh người gọi được
phép thay mặt nhóm, ghim `teamId` về đúng nhóm đó, và từ chối động vào truyện của nhóm
khác.

### Cấu trúc bên trong một module

```
com.storyplatform.<module>/
├── api/               ← @RestController, DTO request/response
├── application/       ← service, logic nghiệp vụ, dto/
├── domain/            ← entity, enum
└── infrastructure/    ← repository
```

Không phải module nào cũng có đủ bốn tầng. `promotion` chẳng hạn chỉ có `api/` và
`application/` — nó thao tác bằng `JdbcClient` chứ không ánh xạ entity.

### JdbcClient hay Repository

Cả hai đều dùng, có chủ đích:

- **Spring Data JDBC repository** cho thực thể đơn giản, đọc/ghi cả cục
- **`JdbcClient` thẳng** cho truy vấn phức tạp, cập nhật có điều kiện, và mọi chỗ động
  tới tiền

Có một cái bẫy đã cắn một lần: `repository.save()` với `@Id` đã gán giá trị sẽ phát
**UPDATE**, không phải INSERT. Chuyện này làm mọi lượt ghi `ad_events` trả 500 với lỗi
`Id ... not found in database`. Sinh id ở tầng ứng dụng thì phải INSERT tường minh.

---

## Frontend

![Frontend](images/modules-2-frontend.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
graph TB
    subgraph app["fe/apps/web"]
        MAIN["main.tsx → App.tsx<br/>60 route"]
        PAGES["pages/<br/>theo cấu trúc thư mục = URL"]
        COMP["components/<br/>66 component dùng chung"]
        FEAT["features/<br/>gom theo tính năng"]
        LIB["lib/<br/>gọi API, auth, format"]
        CSS["pages/globals.css<br/>~10.700 dòng"]
    end

    subgraph pkg["fe/packages"]
        API["api-client<br/>kiểu và hàm gọi API"]
        UI["ui<br/>BrandMark, primitive"]
        CFG["config"]
    end

    MAIN --> PAGES
    PAGES --> COMP
    PAGES --> FEAT
    COMP --> LIB
    LIB --> API
    COMP --> UI
    PAGES --> CSS
```

</details>

### CSS: một file toàn cục và vài CSS Module

Phần lớn kiểu dáng nằm trong `pages/globals.css` (~10.700 dòng), một số component có
`.module.css` riêng.

Cách này đã sinh ra **một lớp lỗi lặp đi lặp lại**, đáng ghi lại để không mắc nữa:

```css
.catalogCardBody h3 a          { color: #0f172a !important; }  /* thắng */
[data-theme="dark"] … h3 a     { color: #f8fafc; }             /* thua */
```

Rule dark có specificity cao hơn, **nhưng `!important` thắng bất kể specificity**. Tên
truyện vì thế luôn đen, kể cả ở theme tối. Cách sửa không phải là thêm `!important` vào
rule dark, mà là **dùng token**: `var(--text-primary)` tự đổi theo theme, và cả lớp lỗi
biến mất.

Luật rút ra: **màu không bao giờ được định nghĩa duy nhất bên trong một khối
`@media` hay `[data-theme]`.** Định nghĩa ở token trên `:root`, rồi mới đổi giá trị token.

### Route được sinh từ cấu trúc thư mục

`pages/teams/[teamId]/pr/page.tsx` → `/teams/:teamId/pr`

Nhưng `App.tsx` phải khai báo route **thủ công**. Tạo file mà quên khai báo thì route
không tồn tại, không có lỗi nào báo.

---

## Đường đi của một request

![Đường đi của một request](images/modules-3-duong-di-cua-mot-request.png)

<details><summary>Mã nguồn sơ đồ</summary>

```mermaid
sequenceDiagram
    participant B as Trình duyệt
    participant N as nginx
    participant S as Spring Security
    participant C as Controller
    participant SV as Service
    participant DB as MySQL

    B->>N: GET /api/v1/stories/abc
    N->>S: chuyển tiếp
    S->>S: khớp route với chuỗi luật
    Note over S: Route không khai báo<br/>rơi vào denyAll → 403
    S->>C: Jwt principal (hoặc null)
    C->>SV: gọi service
    SV->>DB: JdbcClient
    DB-->>SV: ResultSet
    SV-->>C: DTO
    C-->>B: JSON
```

</details>

Chỗ hay bẫy: **một route không khai báo trong `SecurityConfiguration` sẽ trả 403**, đọc
như lỗi phân quyền trong khi thật ra là quên khai báo. Đã cắn ở `/stories/*/combo-purchase`
và `/teams/*/published-stories`.
