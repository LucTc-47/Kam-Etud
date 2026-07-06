package com.kametude.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionNotFound(TransactionNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(InvalidTransactionStatusException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStatus(InvalidTransactionStatusException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentException(PaymentException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "TRANSACTION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PAYMENT_PROVIDER_UNREACHABLE", "BUSINESS_UNAVAILABLE", "IDENTITY_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "PAYMENT_PROVIDER_ERROR" -> HttpStatus.BAD_GATEWAY;
            case "PAYMENT_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "INVALID_PHONE", "INVALID_PAYER_PHONE", "INVALID_SELLER_PHONE",
                 "SELLER_PHONE_MISSING", "INVALID_AMOUNT" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return buildResponse(status, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericError(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur interne est survenue. Veuillez contacter le support.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", errorCode);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
