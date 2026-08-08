package com.storyplatform.catalog.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Category(
        String id,
        String slug,
        String name,
        Group group,
        int sortOrder,
        boolean active,
        long version
) {

    private static final Pattern SLUG = Pattern.compile(
            "[a-z0-9]+(?:-[a-z0-9]+)*"
    );

    public Category {
        id = requireText(id, "id");
        slug = requireText(slug, "slug");
        name = requireText(name, "name");
        group = Objects.requireNonNull(group, "group");
        if (!SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "slug must be lowercase kebab-case"
            );
        }
        if (sortOrder < 0 || version < 1) {
            throw new IllegalArgumentException(
                    "sortOrder and version are invalid"
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public enum Group {
        GENRE("genre", "Thể loại", 10),
        SETTING("setting", "Bối cảnh", 20),
        TONE("tone", "Sắc thái", 30),
        ENDING("ending", "Kết truyện", 40),
        RELATIONSHIP("relationship", "Quan hệ", 50),
        FORMAT("format", "Hình thức", 60);

        private final String key;
        private final String label;
        private final int sortOrder;

        Group(String key, String label, int sortOrder) {
            this.key = key;
            this.label = label;
            this.sortOrder = sortOrder;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public int sortOrder() {
            return sortOrder;
        }

        public static Group fromKey(String value) {
            String normalized = Objects.requireNonNull(value, "value")
                    .toLowerCase(Locale.ROOT);
            for (Group candidate : values()) {
                if (candidate.key.equals(normalized)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException(
                    "unsupported category group: " + value
            );
        }
    }
}
