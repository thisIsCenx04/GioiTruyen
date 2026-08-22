package com.storyplatform.catalog.api;

import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thêm thể loại mà file truyện có nhắc tới nhưng trang chưa có.
 *
 * <p>File truyện ghi sẵn dòng "Thể loại: Ngôn Tình, Vô Hạn Lưu". Khi một tên
 * trong đó chưa có trong danh sách của trang, người upload trước đây không có
 * lối nào: hoặc bỏ thể loại đó đi, hoặc nhờ quản trị viên thêm rồi upload lại.
 * Người upload thường chính là chủ nhóm, không phải quản trị viên, nên màn hình
 * quản trị thể loại nằm ngoài tầm với của họ.
 *
 * <p>Ở đây họ tự thêm được, nhưng chỉ khi bấm nút xác nhận trong hộp thoại báo
 * upload xong - không tự động. Một tên gõ sai trong file mà lặng lẽ thành một
 * thể loại mới thì danh sách thể loại sẽ đầy rác trong vài tuần, và không ai
 * biết rác từ đâu ra.
 */
@RestController
@RequestMapping("/genres")
public class GenreSuggestionController {

    /** Đủ dài cho mọi thể loại có thật, đủ ngắn để chặn một câu văn lọt vào. */
    private static final int MAX_NAME_LENGTH = 60;

    /** Chặn một lần bấm nhầm biến thành hai chục thể loại mới. */
    private static final int MAX_PER_REQUEST = 10;

    private final JdbcClient jdbc;

    public GenreSuggestionController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record AddGenresRequest(List<String> names) {}

    public record AddedGenre(String id, String name, String slug, boolean created) {}

    /**
     * Thêm những thể loại còn thiếu và trả về id của chúng.
     *
     * <p>Gọi lại với cùng một tên không tạo thêm bản ghi: tên đã có sẽ được
     * trả về nguyên id cũ kèm {@code created = false}. Nhờ vậy hai người cùng
     * upload một bộ truyện không đẻ ra hai thể loại trùng tên.
     */
    @PostMapping
    @Transactional
    public List<AddedGenre> add(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AddGenresRequest request
    ) {
        if (jwt == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "genre.unauthenticated",
                    "Authentication required", "Bạn cần đăng nhập để thêm thể loại.");
        }
        List<String> names = request == null || request.names() == null ? List.of() : request.names();
        if (names.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "genre.no_names",
                    "No names", "Chưa có tên thể loại nào để thêm.");
        }
        if (names.size() > MAX_PER_REQUEST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "genre.too_many",
                    "Too many", "Mỗi lần chỉ thêm được tối đa %d thể loại.".formatted(MAX_PER_REQUEST));
        }

        List<AddedGenre> result = new java.util.ArrayList<>();
        for (String raw : names) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) continue;
            if (name.length() > MAX_NAME_LENGTH) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "genre.name_too_long",
                        "Name too long",
                        "Tên thể loại \"%s…\" quá dài. Tối đa %d ký tự."
                                .formatted(name.substring(0, 20), MAX_NAME_LENGTH));
            }
            result.add(addOne(name));
        }
        return result;
    }

    private AddedGenre addOne(String name) {
        String slug = slugify(name);

        // Tìm theo slug chứ không theo tên: "Vô Hạn Lưu" và "vô hạn lưu" là một
        // thể loại, và slug chính là dạng đã bỏ dấu, bỏ hoa thường của tên.
        var existing = jdbc.sql("SELECT id, name, slug FROM genres WHERE slug = ? LIMIT 1")
                .param(slug)
                .query((rs, rowNum) -> new AddedGenre(
                        rs.getString("id"), rs.getString("name"), rs.getString("slug"), false))
                .optional();
        if (existing.isPresent()) return existing.get();

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("""
                        INSERT INTO genres (id, name, slug, description, is_active, created_at, updated_at)
                        VALUES (?, ?, ?, '', 1, ?, ?)
                        """)
                .params(id.toString(), name, slug,
                        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                .update();
        return new AddedGenre(id.toString(), name, slug, true);
    }

    /** Chữ thường không dấu, nối bằng gạch ngang - đúng dạng slug của trang. */
    private static String slugify(String value) {
        String plain = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (plain.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "genre.name_invalid",
                    "Invalid name", "Tên thể loại \"%s\" không dùng được.".formatted(value));
        }
        return plain;
    }
}
