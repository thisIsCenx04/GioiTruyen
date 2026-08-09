package com.storyplatform.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("user_settings")
public class UserSetting {
    @Id
    private UUID userId;
    private ThemeMode theme;
    private Integer readerFontSize;
    private String readerFontFamily;
    private java.math.BigDecimal readerLineHeight;
    private Instant updatedAt;

    public UserSetting() {}

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public ThemeMode getTheme() { return theme; }
    public void setTheme(ThemeMode theme) { this.theme = theme; }

    public Integer getReaderFontSize() { return readerFontSize; }
    public void setReaderFontSize(Integer readerFontSize) { this.readerFontSize = readerFontSize; }

    public String getReaderFontFamily() { return readerFontFamily; }
    public void setReaderFontFamily(String readerFontFamily) { this.readerFontFamily = readerFontFamily; }

    public java.math.BigDecimal getReaderLineHeight() { return readerLineHeight; }
    public void setReaderLineHeight(java.math.BigDecimal readerLineHeight) { this.readerLineHeight = readerLineHeight; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
