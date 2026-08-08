package com.storyplatform.unit.sharedapi;

import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApiExceptionTest {

    @Test
    void exceptionRetainsOnlyItsStableApiContract() {
        ApiException exception = new ApiException(
                HttpStatus.CONFLICT,
                "CONCURRENCY_CONFLICT",
                "Dữ liệu đã thay đổi",
                "Hãy tải phiên bản mới nhất."
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo("CONCURRENCY_CONFLICT");
        assertThat(exception.title()).isEqualTo("Dữ liệu đã thay đổi");
        assertThat(exception).hasMessage("Hãy tải phiên bản mới nhất.");
    }

    @Test
    void blankStableCodeIsRejectedAtConstructionTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                " ",
                "Yêu cầu lỗi",
                "Chi tiết"
        ));
    }

    @Test
    void blankTitleIsRejectedAtConstructionTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "",
                "Chi tiết"
        ));
    }

    @Test
    void nullStableCodeIsRejectedAtConstructionTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                null,
                "Yêu cầu lỗi",
                "Chi tiết"
        ));
    }
}
