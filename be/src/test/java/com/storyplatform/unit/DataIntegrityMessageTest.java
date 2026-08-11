package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A database rejection is nearly always caused by what the admin submitted, so
 * the reply has to name the offending field rather than say "system error".
 */
class DataIntegrityMessageTest {

    private String describe(String rawMysqlMessage) {
        return (String) ReflectionTestUtils.invokeMethod(
                ApiExceptionHandler.class, "describeDataIntegrityFailure", rawMysqlMessage);
    }

    @Test
    void namesTheFieldThatWasTooLong() {
        String detail = describe(
                "Data truncation: Data too long for column 'short_description' at row 1");

        assertThat(detail).contains("Giới thiệu");
        assertThat(detail).contains("quá dài");
    }

    @Test
    void namesAnOverlongTitle() {
        String detail = describe("Data truncation: Data too long for column 'title' at row 1");

        assertThat(detail).contains("Tên truyện");
    }

    @Test
    void reportsAnUnknownColumnByName() {
        String detail = describe("Data truncation: Data too long for column 'banner_url' at row 1");

        assertThat(detail).contains("banner_url");
    }

    @Test
    void explainsADuplicateValue() {
        String detail = describe(
                "Duplicate entry 'tuyet-tan-kien-quan-tam' for key 'stories.slug'");

        assertThat(detail).contains("tuyet-tan-kien-quan-tam");
        assertThat(detail).contains("đã tồn tại");
    }

    @Test
    void explainsAMissingRequiredField() {
        String detail = describe("Column 'team_id' cannot be null");

        assertThat(detail).contains("team_id");
        assertThat(detail).contains("không được để trống");
    }

    @Test
    void explainsABrokenReference() {
        String detail = describe(
                "Cannot add or update a child row: a foreign key constraint fails");

        assertThat(detail).contains("tham chiếu");
    }

    @Test
    void fallsBackToSomethingActionable() {
        String detail = describe("some driver message nobody anticipated");

        assertThat(detail).isNotBlank();
        // Must still point at the submitted data rather than blame the server.
        assertThat(detail).contains("kiểm tra lại");
    }
}
