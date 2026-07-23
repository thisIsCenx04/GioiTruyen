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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String SAFE_INTERNAL_DETAIL =
            "Yêu cầu không thể được xử lý. Vui lòng thử lại sau.";
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
        return ResponseEntity.status(exception.status()).body(problem);
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
                        + exception.getClass().getName()
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
                SAFE_REQUEST_DETAIL,
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
}
