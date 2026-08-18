package com.storyplatform.admin.application;

import com.storyplatform.shared.api.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local disk storage for admin-uploaded story assets. Cloudinary is the intended
 * production target, but it is disabled outside deployed environments, so covers
 * are written under a served directory and referenced by a public URL path.
 */
@Service
public class StoryMediaStorage {

    private static final long MAX_COVER_BYTES = 5L * 1024 * 1024;
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    /** A hero banner is full-bleed, so it carries more pixels than a cover. */
    private static final long MAX_BANNER_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final Path uploadRoot;
    private final String publicPrefix;
    private final CoverThumbnails thumbnails;

    public StoryMediaStorage(
            @Value("${app.media.local.upload-dir:uploads}") String uploadDir,
            @Value("${app.media.local.public-prefix:/uploads}") String publicPrefix,
            CoverThumbnails thumbnails
    ) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix.replaceAll("/+$", "");
        this.thumbnails = thumbnails;
    }

    /** Returns the public URL for the stored cover, or null when no file was sent. */
    public String storeCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_COVER_BYTES) {
            throw badRequest("story.cover_too_large", "Cover image is larger than 5MB");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw badRequest("story.cover_unsupported",
                    "Cover image must be a PNG, JPEG, WEBP or GIF file");
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw badRequest("story.cover_unsupported",
                    "Cover image must be a PNG, JPEG, WEBP or GIF file");
        }

        // The name is generated rather than taken from the upload so a crafted
        // filename cannot escape the upload directory or overwrite another story.
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = uploadRoot.resolve("stories").resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw badRequest("story.cover_invalid", "Resolved upload path is outside the upload directory");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "story.cover_write_failed",
                    "Could not store cover", "Could not store the cover image: " + exception.getMessage());
        }
        // Cards read the card-sized copy; the original stays for the detail page.
        thumbnails.generate(target);
        return publicPrefix + "/stories/" + storedName;
    }

    /**
     * Stores a reader's avatar and returns its public URL.
     *
     * <p>Smaller cap than a cover: an avatar is displayed at a few dozen
     * pixels, so anything larger is wasted bandwidth on every page it appears.
     */
    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("avatar.missing", "No avatar file was uploaded");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw badRequest("avatar.too_large", "Ảnh đại diện tối đa 2MB.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw badRequest("avatar.unsupported",
                    "Ảnh đại diện phải là PNG, JPEG, WEBP hoặc GIF.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw badRequest("avatar.unsupported",
                    "Ảnh đại diện phải là PNG, JPEG, WEBP hoặc GIF.");
        }

        // Generated name, as with covers: a crafted filename must not be able
        // to escape the upload directory or overwrite someone else's file.
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = uploadRoot.resolve("avatars").resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw badRequest("avatar.invalid", "Resolved upload path is outside the upload directory");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "avatar.write_failed",
                    "Could not store avatar", "Không lưu được ảnh đại diện: " + exception.getMessage());
        }
        return publicPrefix + "/avatars/" + storedName;
    }

    /**
     * Stores a page banner and returns its public URL.
     *
     * <p>A banner spans the full width of a hero section, so it is allowed to be
     * heavier than a cover.
     */
    public String storeBanner(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("banner.missing", "Chưa chọn ảnh banner.");
        }
        if (file.getSize() > MAX_BANNER_BYTES) {
            throw badRequest("banner.too_large", "Ảnh banner tối đa 8MB.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw badRequest("banner.unsupported", "Ảnh banner phải là PNG, JPEG, WEBP hoặc GIF.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw badRequest("banner.unsupported", "Ảnh banner phải là PNG, JPEG, WEBP hoặc GIF.");
        }

        // Generated name, as with covers and avatars: a crafted filename must
        // not be able to escape the upload directory.
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = uploadRoot.resolve("banners").resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw badRequest("banner.invalid", "Resolved upload path is outside the upload directory");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "banner.write_failed",
                    "Could not store banner", "Không lưu được ảnh banner: " + exception.getMessage());
        }
        return publicPrefix + "/banners/" + storedName;
    }

    /** Reads an uploaded chapter file as UTF-8 text. */
    public String readTextFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_TEXT_BYTES) {
            throw badRequest("story.chapter_too_large", "Chapter file is larger than 2MB");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw badRequest("story.chapter_unreadable",
                    "Could not read the chapter file: " + exception.getMessage());
        }
    }

    public Path uploadRoot() {
        return uploadRoot;
    }

    /**
     * Builds the thumbnails of covers uploaded before thumbnails existed.
     *
     * <p>On its own thread: it reads every cover on disk, and a slow first boot
     * would delay the health check the deploy waits on. Nothing depends on it
     * having finished, because a cover with no thumbnail still renders.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillCoverThumbnails() {
        Thread.ofVirtual().name("cover-thumbnail-backfill").start(() -> {
            int built = thumbnails.backfill(uploadRoot.resolve("stories"));
            if (built > 0) {
                LoggerFactory.getLogger(StoryMediaStorage.class)
                        .info("Built {} missing cover thumbnails", built);
            }
        });
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, "Invalid upload", detail);
    }
}
