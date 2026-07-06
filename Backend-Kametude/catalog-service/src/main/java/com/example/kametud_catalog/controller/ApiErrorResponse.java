package com.example.kametud_catalog.controller;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> details
) {

    public static ApiErrorResponse of(int status, String error, String message) {
        return new ApiErrorResponse(OffsetDateTime.now(), status, error, message, Map.of());
    }

    public static ApiErrorResponse withDetails(
            int status,
            String error,
            String message,
            Map<String, String> details
    ) {
        return new ApiErrorResponse(OffsetDateTime.now(), status, error, message, details);
    }
}
