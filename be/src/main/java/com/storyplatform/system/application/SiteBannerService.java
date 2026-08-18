package com.storyplatform.system.application;

import com.storyplatform.admin.application.StoryMediaStorage;
import com.storyplatform.shared.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Hero banner images an admin can replace without a deploy.
 *
 * <p>Each slot is one page's banner. The uploaded file lands in the media
 * directory and only its public URL is kept, in {@code site_settings} under
 * {@code banner.<slot>}, so a slot with no row keeps whatever the page draws by
 * itself. The recommended pixel size travels with the slot: the banner is
 * rendered with {@code object-fit: cover}, so anything of that aspect ratio
 * fills the hero without distortion, and anything far off it gets cropped.
 */
@Service
public class SiteBannerService {

    /** Everything the admin screen needs to render one slot. */
    public record BannerSlot(
            String slot,
            String label,
            String pagePath,
            String description,
            int recommendedWidth,
            int recommendedHeight,
            String imageUrl
    ) {
    }

    private record SlotDefinition(
            String slot, String label, String pagePath, String description, int width, int height) {
    }

    private static final String SETTING_PREFIX = "banner.";

    /**
     * 3:1 keeps the hero shallow enough that the controls below it stay above
     * the fold on a laptop.
     */
    private static final List<SlotDefinition> SLOTS = List.of(
            new SlotDefinition("rankings", "Bảng xếp hạng", "/rankings",
                    "Ảnh nền banner đầu trang Bảng xếp hạng.", 1920, 640),
            new SlotDefinition("stories", "Kho truyện", "/stories",
                    "Ảnh nền banner đầu trang Kho truyện.", 1920, 640),
            new SlotDefinition("categories", "Thể loại", "/categories",
                    "Ảnh nền banner đầu trang Thể loại.", 1920, 640),
            new SlotDefinition("teams", "Nhóm xuất bản", "/teams",
                    "Ảnh nền banner đầu trang Nhóm xuất bản.", 1920, 640),
            new SlotDefinition("audio", "Nghe audio", "/audio",
                    "Ảnh nền banner đầu trang Nghe audio.", 1920, 640),
            new SlotDefinition("zhihu", "Truyện Zhihu", "/zhihu",
                    "Ảnh nền banner đầu trang Truyện Zhihu.", 1920, 640)
    );

    private final JdbcClient jdbc;
    private final StoryMediaStorage mediaStorage;

    public SiteBannerService(JdbcClient jdbc, StoryMediaStorage mediaStorage) {
        this.jdbc = jdbc;
        this.mediaStorage = mediaStorage;
    }

    /** Slot definitions with whichever image each one currently carries. */
    public List<BannerSlot> slots() {
        Map<String, String> stored = storedUrls();
        return SLOTS.stream()
                .map(definition -> describe(definition, stored.get(definition.slot())))
                .toList();
    }

    /** Slot to image URL, for the public pages. Slots without an image are omitted. */
    public Map<String, String> publicBanners() {
        return storedUrls();
    }

    @Transactional
    public BannerSlot upload(String slot, MultipartFile file) {
        SlotDefinition definition = definitionOf(slot);
        String imageUrl = mediaStorage.storeBanner(file);
        jdbc.sql("""
                        INSERT INTO site_settings (`key`, setting_key, value, updated_at)
                        VALUES (:key, :key, JSON_QUOTE(:url), NOW())
                        ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = NOW()
                        """)
                .param("key", SETTING_PREFIX + definition.slot())
                .param("url", imageUrl)
                .update();
        return describe(definition, imageUrl);
    }

    /** Drops the override so the page falls back to its built-in banner. */
    @Transactional
    public BannerSlot reset(String slot) {
        SlotDefinition definition = definitionOf(slot);
        jdbc.sql("DELETE FROM site_settings WHERE `key` = :key")
                .param("key", SETTING_PREFIX + definition.slot())
                .update();
        return describe(definition, null);
    }

    private static BannerSlot describe(SlotDefinition definition, String imageUrl) {
        return new BannerSlot(
                definition.slot(),
                definition.label(),
                definition.pagePath(),
                definition.description(),
                definition.width(),
                definition.height(),
                imageUrl
        );
    }

    private Map<String, String> storedUrls() {
        Map<String, String> urls = new LinkedHashMap<>();
        jdbc.sql("SELECT `key` AS setting_key, JSON_UNQUOTE(value) AS image_url "
                        + "FROM site_settings WHERE `key` LIKE :prefix")
                .param("prefix", SETTING_PREFIX + "%")
                .query()
                .listOfRows()
                .forEach(row -> {
                    String key = String.valueOf(row.get("setting_key"));
                    Object imageUrl = row.get("image_url");
                    if (imageUrl != null) {
                        urls.put(key.substring(SETTING_PREFIX.length()), String.valueOf(imageUrl));
                    }
                });
        return urls;
    }

    private SlotDefinition definitionOf(String slot) {
        return SLOTS.stream()
                .filter(definition -> definition.slot().equals(slot))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "banner.slot_unknown",
                        "Unknown banner slot", "Không có vị trí banner này."));
    }
}
