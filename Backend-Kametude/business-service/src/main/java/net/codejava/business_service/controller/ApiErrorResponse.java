package net.codejava.business_service.controller;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorResponse(OffsetDateTime timestamp, int status, String error,
                               String message, Map<String, String> details) {
}
