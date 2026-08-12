package com.storyplatform.media.api;

import com.storyplatform.admin.application.StoryMediaStorage;
import com.storyplatform.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uploading a profile picture.
 *
 * <p>The file is stored the same way story covers are, then the resulting URL
 * is written to the account. Readers previously had to paste a link to an image
 * hosted somewhere else, which most people have no way of producing.
 */
@RestController
public class AvatarUploadController {

    private final StoryMediaStorage storage;
    private final JdbcClient jdbc;

    public AvatarUploadController(StoryMediaStorage storage, JdbcClient jdbc) {
        this.storage = storage;
        this.jdbc = jdbc;
    }

    public record AvatarResponse(String avatarUrl) {
    }

    @PostMapping("/me/avatar")
    @Transactional
    public AvatarResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file
    ) {
        String userId = requireUser(jwt);
        String url = storage.storeAvatar(file);

        // users.avatar_url is the only copy; user_profiles holds bio and cover
        // but no avatar column, so there is nothing to keep in sync here.
        jdbc.sql("UPDATE users SET avatar_url = ?, updated_at = NOW() WHERE id = ?")
                .params(url, userId)
                .update();

        return new AvatarResponse(url);
    }

    private static String requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để đổi ảnh đại diện.");
        }
        return UUID.fromString(jwt.getSubject()).toString();
    }
}
