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
import org.springframework.beans.factory.annotation.Value;
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
    private static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "gif");

    private final Path uploadRoot;
    private final String publicPrefix;

    public StoryMediaStorage(
            @Value("${app.media.local.upload-dir:uploads}") String uploadDir,
            @Value("${app.media.local.public-prefix:/uploads}") String publicPrefix
    ) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix.replaceAll("/+$", "");
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
        return publicPrefix + "/stories/" + storedName;
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
