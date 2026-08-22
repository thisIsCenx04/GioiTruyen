package com.storyplatform.tts.api;

import com.storyplatform.catalog.application.PublicCatalogService;
import com.storyplatform.catalog.application.dto.CatalogDtos;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.tts.application.ChapterTextSplitter;
import com.storyplatform.tts.application.TtsService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bản đọc tự động: chữ của chương, tiếng do máy chủ dựng.
 *
 * <p>Hai lối vào. `manifest` trả về đúng những đoạn văn và những mẩu mà máy chủ
 * sẽ đọc - trình duyệt dùng chính danh sách này để hiển thị và tô sáng, nên
 * không tồn tại "một bản chữ để nhìn, một bản khác để đọc". `audio` trả tiếng
 * của một mẩu.
 *
 * <p>Chữ lấy qua {@link PublicCatalogService#chapter}, tức là đi qua đúng cửa
 * kiểm tra trả phí mà trang đọc dùng: chương chưa mở khoá về đây với phần chữ
 * rỗng, nên không có đường nào biến bản đọc thành cách nghe lậu chương trả phí.
 */
@RestController
public class TtsController {

    private final PublicCatalogService catalog;
    private final TtsService tts;

    public TtsController(PublicCatalogService catalog, TtsService tts) {
        this.catalog = catalog;
        this.tts = tts;
    }

    /** Đoạn để hiển thị và mẩu để đọc, cùng một lần cắt. */
    public record Manifest(
            String chapterId,
            String title,
            int version,
            List<String> paragraphs,
            List<ChapterTextSplitter.SpeechChunk> chunks,
            boolean available
    ) {}

    @GetMapping("/chapters/{chapterId}/tts/manifest")
    public Manifest manifest(@PathVariable String chapterId, @AuthenticationPrincipal Jwt jwt) {
        CatalogDtos.PublishedChapterDetail chapter = requireReadable(chapterId, jwt);
        ChapterTextSplitter.ChapterSpeech speech = ChapterTextSplitter.split(chapter.contentHtml());
        return new Manifest(chapter.id(), chapter.title(), chapter.version(),
                speech.paragraphs(), speech.chunks(), tts.available());
    }

    @GetMapping("/chapters/{chapterId}/tts/{index}.opus")
    public ResponseEntity<byte[]> audio(
            @PathVariable String chapterId,
            @PathVariable int index,
            @RequestParam(defaultValue = "female") String voice,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CatalogDtos.PublishedChapterDetail chapter = requireReadable(chapterId, jwt);
        // Cắt lại từ chữ gốc chứ không nhận chữ từ trình duyệt gửi lên: nhận
        // chữ tự do là biến máy chủ thành một dịch vụ đọc thuê miễn phí cho bất
        // kỳ ai, và mở đường cho việc đọc ra thứ không có trong truyện.
        List<ChapterTextSplitter.SpeechChunk> chunks =
                ChapterTextSplitter.split(chapter.contentHtml()).chunks();
        if (index < 0 || index >= chunks.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "tts.chunk_not_found",
                    "Chunk not found", "Không tìm thấy đoạn cần đọc.");
        }

        byte[] audio = tts.audio(chapter.id(), chapter.version(), index,
                chunks.get(index).text(), TtsService.Voice.of(voice));

        return ResponseEntity.ok()
                // Tiếng của một mẩu không bao giờ đổi khi chương chưa đổi, mà
                // version nằm trong đường dẫn đệm, nên trình duyệt giữ được lâu.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .contentType(MediaType.parseMediaType("audio/ogg"))
                .body(audio);
    }

    /** Tình trạng máy dựng tiếng, cho màn hình quản trị. */
    @GetMapping("/admin/tts/status")
    public Map<String, Object> status() {
        return tts.cacheStats();
    }

    /**
     * Chương mà người gọi thật sự đọc được.
     *
     * <p>Chương trả phí chưa mở khoá về đây với phần chữ rỗng - đó là cách
     * {@link PublicCatalogService} giữ cửa, và bản đọc dựa vào đúng cửa ấy.
     */
    private CatalogDtos.PublishedChapterDetail requireReadable(String chapterId, Jwt jwt) {
        CatalogDtos.PublishedChapterDetail chapter =
                catalog.chapter(chapterId, jwt == null ? null : jwt.getSubject(), isAdmin(jwt));
        if (chapter.contentHtml() == null || chapter.contentHtml().isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "tts.locked",
                    "Chapter locked", "Chương này chưa mở khoá nên chưa nghe được.");
        }
        return chapter;
    }

    /**
     * Quản trị viên nhìn thấy mọi chương, kể cả chương trả phí.
     *
     * <p>Ba tên claim vì token cũ và token mới đặt vai ở ba chỗ khác nhau; đây
     * là bản sao đúng của cách {@code PublicCatalogController} nhận diện, để
     * hai màn hình không thể bất đồng về việc ai là quản trị viên.
     */
    private static boolean isAdmin(Jwt jwt) {
        if (jwt == null) return false;
        Object role = jwt.getClaim("role");
        if (role != null && "ADMIN".equalsIgnoreCase(role.toString())) return true;
        Object scope = jwt.getClaim("scope");
        if (scope != null && scope.toString().toUpperCase().contains("ADMIN")) return true;
        Object roles = jwt.getClaim("roles");
        if (roles instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && "ADMIN".equalsIgnoreCase(item.toString())) return true;
            }
        }
        return false;
    }
}
