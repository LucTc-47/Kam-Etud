package com.example.kametud_catalog.controller;

import com.example.kametud_catalog.exception.GigNotFoundException;
import com.example.kametud_catalog.exception.IdentityServiceUnavailableException;
import com.example.kametud_catalog.exception.StudentPublicationForbiddenException;
import com.example.kametud_catalog.exception.CatalogAccessDeniedException;
import com.example.kametud_catalog.exception.DuplicateReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));

        return ApiErrorResponse.withDetails(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid gig payload",
                details
        );
    }

    @ExceptionHandler(GigNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleGigNotFound(GigNotFoundException exception) {
        return ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(StudentPublicationForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleStudentPublicationForbidden(StudentPublicationForbiddenException exception) {
        return ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(CatalogAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleAccessDenied(CatalogAccessDeniedException exception) {
        return ApiErrorResponse.of(403, HttpStatus.FORBIDDEN.getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(DuplicateReferenceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleDuplicate(DuplicateReferenceException exception) {
        return ApiErrorResponse.of(409, HttpStatus.CONFLICT.getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return ApiErrorResponse.of(404, HttpStatus.NOT_FOUND.getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(IdentityServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleIdentityServiceUnavailable(IdentityServiceUnavailableException exception) {
        return ApiErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                exception.getMessage()
        );
    }
}
