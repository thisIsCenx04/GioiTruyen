package com.storyplatform.media.domain;

import java.util.Set;

public enum UploadPurpose {
    AVATAR(
            MediaOwnerType.USER,
            5L * 1024 * 1024,
            null
    ),
    TEAM_LOGO(
            MediaOwnerType.TEAM,
            5L * 1024 * 1024,
            "team:manage"
    ),
    STORY_COVER(
            MediaOwnerType.TEAM,
            10L * 1024 * 1024,
            "story:edit"
    ),
    CHAPTER_IMAGE(
            MediaOwnerType.TEAM,
            15L * 1024 * 1024,
            "story:edit"
    );

    private static final Set<String> CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final MediaOwnerType ownerType;
    private final long maximumBytes;
    private final String permission;

    UploadPurpose(
            MediaOwnerType ownerType,
            long maximumBytes,
            String permission
    ) {
        this.ownerType = ownerType;
        this.maximumBytes = maximumBytes;
        this.permission = permission;
    }

    public MediaOwnerType ownerType() {
        return ownerType;
    }

    public long maximumBytes() {
        return maximumBytes;
    }

    public String permission() {
        return permission;
    }

    public Set<String> allowedContentTypes() {
        return CONTENT_TYPES;
    }
}
