package com.kametude.support_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SupportException extends RuntimeException {
    private final HttpStatus status;
    public SupportException(HttpStatus status, String message) { super(message); this.status = status; }
}
