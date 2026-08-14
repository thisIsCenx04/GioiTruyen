# Rule: Upload VPS - An Toàn Dữ Liệu Production

## TUYỆT ĐỐI TUÂN THỦ khi deploy lên VPS

### 1. BACKUP TRƯỚC KHI UPLOAD
- **BẮT BUỘC** backup dữ liệu production về máy local trước khi upload bất kỳ thay đổi nào lên VPS.
- Backup bao gồm: database dump (mysqldump), file uploads/media, và file `.env` cấu hình.
- Lưu backup tại thư mục local với timestamp rõ ràng (VD: `backup_2026-08-14_22h00`).
- Chỉ tiến hành upload SAU KHI backup hoàn tất và xác nhận file backup hợp lệ.

### 2. KHÔNG UPLOAD SEED DATA LÊN PRODUCTION
- **NGHIÊM CẤM** upload bất kỳ file nào trong thư mục `db/seed/` lên VPS production.
- Các file khớp pattern `seed|sample|demo|fixture` **KHÔNG BAO GIỜ** được phép upload.
- Script `upload_vps.ps1` đã có cơ chế chặn (`$SeedFilePattern`), nhưng agent cũng phải tuân thủ và không bao giờ cố bypass.
- Production đã có dữ liệu thật của người dùng — ghi đè seed sẽ **MẤT TOÀN BỘ DỮ LIỆU**.

### 3. CHỈ UPLOAD CÁC ARTIFACT BUILD
Chỉ được phép upload lên VPS:
- `be/target/*.jar` — Backend Spring Boot fat JAR
- `fe/apps/web/dist/` — Frontend SPA build
- `be/src/main/resources/db/migration/*.sql` — Migration SQL mới (không phải seed)

### 4. KHÔNG UPLOAD CÁC FILE SAU
- Source code (`src/`, `node_modules/`, `.git/`)
- File cấu hình local (`.env.local`, `application-local.yml`)
- File seed/sample/demo/fixture data
- File tạm, log, hoặc artifact debug

### 5. QUY TRÌNH DEPLOY CHUẨN
```
1. Build frontend:  pnpm --filter @gioitruyen/web build
2. Build backend:   mvn clean package -DskipTests
3. Chạy upload:     upload_vps.bat (hoặc upload_vps.bat -SkipBuild nếu đã build)
```

Script `upload_vps.ps1` tự động xử lý:
- Chặn seed files
- Đối chiếu migration đã chạy trên VPS (flyway_schema_history)
- Backup JAR cũ thành `application.jar.bak` trước khi ghi đè
- Health check sau deploy
