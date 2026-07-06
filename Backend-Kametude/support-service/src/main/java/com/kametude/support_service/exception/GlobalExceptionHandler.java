package com.kametude.support_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SupportException.class)
    ResponseEntity<Map<String, Object>> handleSupport(SupportException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "timestamp", OffsetDateTime.now().toString(), "status", exception.getStatus().value(),
                "error", exception.getStatus().getReasonPhrase(), "message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(e -> details.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", "Requete invalide", "details", details));
    }
}
