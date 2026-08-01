package com.storyplatform.admin.api;

import com.storyplatform.admin.application.AdminContentException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = AdminContentController.class)
public class AdminContentControllerAdvice {

    @ExceptionHandler(AdminContentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            AdminContentException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "type", "about:blank",
                "title", "Không tìm thấy dữ liệu quản trị",
                "status", HttpStatus.NOT_FOUND.value(),
                "code", exception.code()
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "type", "about:blank",
                "title", "Dữ liệu bị trùng hoặc đang được tham chiếu",
                "status", HttpStatus.CONFLICT.value(),
                "code", "ADMIN_CONTENT_CONFLICT"
        ));
    }
}
