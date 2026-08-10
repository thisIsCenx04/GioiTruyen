package com.storyplatform.shared.config;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

/**
 * Every id/reference column in the schema is VARCHAR(36) holding a textual UUID.
 * Without these converters Spring Data JDBC binds {@link UUID} parameters as
 * binary for MySQL, so UPDATE/DELETE statements match zero rows and surface as
 * "Id ... not found in database".
 */
public final class UuidStringConverters {

    private UuidStringConverters() {}

    @WritingConverter
    public enum UuidToStringConverter implements Converter<UUID, String> {
        INSTANCE;

        @Override
        public String convert(UUID source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public enum StringToUuidConverter implements Converter<String, UUID> {
        INSTANCE;

        @Override
        public UUID convert(String source) {
            String trimmed = source.trim();
            return trimmed.isEmpty() ? null : UUID.fromString(trimmed);
        }
    }
}
