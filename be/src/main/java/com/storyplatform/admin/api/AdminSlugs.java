package com.storyplatform.admin.api;

import java.text.Normalizer;
import java.util.Locale;

final class AdminSlugs {

    private AdminSlugs() {}

    /**
     * Converts Vietnamese titles into ASCII slugs: strips diacritics, folds the
     * Đ/đ pair that decomposition leaves behind, and collapses the rest to dashes.
     */
    static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('Đ', 'D')
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized;
    }
}
