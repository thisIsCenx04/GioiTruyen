package com.storyplatform.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Says outright that the fault is server-side, so the admin does not hunt
    // for a mistake in their own input. The traceId in the body identifies the
    // exact log entry.
    private static final String SAFE_INTERNAL_DETAIL =
            "Lỗi từ phía hệ thống, không phải do dữ liệu bạn nhập. "
                    + "Vui lòng thử lại; nếu vẫn lỗi, gửi mã traceId bên dưới cho kỹ thuật.";
    private static final String SAFE_REQUEST_DETAIL =
            "Yêu cầu không hợp lệ hoặc không được hỗ trợ.";

    private final ApiProblemFactory problems;

    public ApiExceptionHandler(ApiProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problem = problems.create(
                exception.status(),
                exception.code(),
                exception.title(),
                exception.getMessage(),
                request
        );
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(exception.status());
        if (exception.retryAfter() != null) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(exception.retryAfter().toSeconds())
            );
            problem.setProperty(
                    "retryAfterSeconds",
                    exception.retryAfter().toSeconds()
            );
        }
        return response.body(problem);
    }

    /**
     * A multipart upload that the container refused outright. Left to the generic
     * handler it surfaced as an opaque 500, which read as a server crash; the
     * usual cause is a story carrying more chapters than the configured part
     * limit, and the admin needs to be told exactly that.
     */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    ResponseEntity<ProblemDetail> handleMultipartException(
            org.springframework.web.multipart.MultipartException exception,
            HttpServletRequest request
    ) {
        logger.warn(
                "Multipart request rejected; correlationId="
                        + CorrelationId.from(request)
                        + "; cause="
                        + rootCauseName(exception)
        );

        String detail = isPartCountFailure(exception)
                ? "Truyện có quá nhiều chương để tải lên trong một lần. "
                        + "Hãy lưu truyện trước, sau đó thêm chương theo từng đợt nhỏ hơn."
                : "Không đọc được dữ liệu tải lên. Nội dung có thể quá lớn hoặc bị gián đoạn "
                        + "khi truyền. Vui lòng thử lại.";

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(problems.create(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "upload.rejected",
                        "Không tải lên được",
                        detail,
                        request
                ));
    }

    /** True when the container rejected the request for having too many parts. */
    private static boolean isPartCountFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (name.contains("FileCountLimitExceeded") || name.contains("SizeLimitExceeded")) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static String rootCauseName(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getName();
    }

    /**
     * A row the database refused. These are almost always caused by the content
     * the admin submitted (a field longer than its column, a duplicate slug), so
     * the reply says which field is at fault instead of a blanket "system error".
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        String raw = String.valueOf(exception.getMostSpecificCause().getMessage());
        logger.warn(
                "Rejected by database; correlationId="
                        + CorrelationId.from(request)
                        + "; cause=" + raw
        );

        String detail = describeDataIntegrityFailure(raw);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(problems.create(
                        HttpStatus.BAD_REQUEST,
                        "request.rejected_by_database",
                        "Dữ liệu không hợp lệ",
                        detail,
                        request
                ));
    }

    /** Turns a MySQL constraint message into something an admin can act on. */
    private static String describeDataIntegrityFailure(String raw) {
        String lower = raw.toLowerCase(java.util.Locale.ROOT);

        if (lower.contains("data too long")) {
            String column = between(raw, "column '", "'");
            String field = switch (column) {
                case "short_description", "description" -> "Giới thiệu";
                case "title" -> "Tên truyện hoặc tên chương";
                case "slug" -> "Đường dẫn (slug)";
                case "original_author" -> "Tác giả";
                default -> column.isEmpty() ? "Một trường" : "Trường \"" + column + "\"";
            };
            return field + " quá dài so với giới hạn cho phép. Hãy rút ngắn rồi lưu lại.";
        }
        if (lower.contains("duplicate entry")) {
            String duplicated = between(raw, "Duplicate entry '", "'");
            return duplicated.isEmpty()
                    ? "Giá trị này đã tồn tại. Hãy đổi tên truyện hoặc đường dẫn rồi thử lại."
                    : "Giá trị \"" + duplicated + "\" đã tồn tại. Hãy đổi rồi thử lại.";
        }
        if (lower.contains("cannot be null")) {
            String column = between(raw, "Column '", "'");
            return column.isEmpty()
                    ? "Thiếu một trường bắt buộc."
                    : "Trường \"" + column + "\" không được để trống.";
        }
        if (lower.contains("foreign key")) {
            return "Dữ liệu tham chiếu không tồn tại (team hoặc thể loại đã bị xóa). "
                    + "Hãy chọn lại rồi lưu.";
        }
        return "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.";
    }

    private static String between(String value, String start, String end) {
        int from = value.indexOf(start);
        if (from < 0) {
            return "";
        }
        int begin = from + start.length();
        int to = value.indexOf(end, begin);
        return to < 0 ? "" : value.substring(begin, to);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        logger.error(
                "Unhandled API exception; correlationId="
                        + CorrelationId.from(request)
                        + "; exceptionType="
                        + exception.getClass().getName(),
                exception
        );
        ProblemDetail problem = problems.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Lỗi hệ thống",
                SAFE_INTERNAL_DETAIL,
                request
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest
    ) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        Map<String, ArrayList<String>> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.computeIfAbsent(
                        error.getField(),
                        ignored -> new ArrayList<>()
                ).add(error.getDefaultMessage() == null
                        ? "Giá trị không hợp lệ."
                        : error.getDefaultMessage())
        );

        ProblemDetail problem = problems.create(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Dữ liệu không hợp lệ",
                "Một hoặc nhiều trường không đáp ứng yêu cầu.",
                request
        );
        problem.setProperty("errors", errors);
        return super.handleExceptionInternal(
                exception,
                problem,
                headers,
                HttpStatus.BAD_REQUEST,
                webRequest
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest
    ) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        ProblemDetail problem = problems.create(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Nội dung JSON không hợp lệ",
                "Nội dung yêu cầu sai cấu trúc hoặc vượt giới hạn cho phép.",
                request
        );
        return super.handleExceptionInternal(
                exception,
                problem,
                headers,
                HttpStatus.BAD_REQUEST,
                webRequest
        );
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest
    ) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        ProblemDetail problem = problems.create(
                statusCode,
                "REQUEST_REJECTED",
                "Yêu cầu bị từ chối",
                explain(exception),
                request
        );
        return super.handleExceptionInternal(
                exception,
                problem,
                headers,
                statusCode,
                webRequest
        );
    }

    /**
     * The sentence to show the caller.
     *
     * <p>A {@link ResponseStatusException} raised by our own controllers carries
     * a reason written in Vietnamese for exactly this purpose - "Vai trò của bạn
     * trong nhóm không được quản lý truyện", say. Replacing every one of them
     * with the generic line left publishers staring at "Yêu cầu không hợp lệ
     * hoặc không được hỗ trợ" with nothing to act on, and hid the real cause
     * from support as well.
     *
     * <p>Everything else keeps the generic line: a type-mismatch or unsupported
     * media type message names internal parameters and classes, which is not the
     * caller's business.
     */
    private static String explain(Exception exception) {
        if (exception instanceof ResponseStatusException statusException) {
            String reason = statusException.getReason();
            if (reason != null && !reason.isBlank()) {
                return reason;
            }
        }
        return SAFE_REQUEST_DETAIL;
    }
}
