package com.darwin.authservice.controller;

import com.darwin.authservice.exception.EmailAlreadyUsedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiErrorResponse> emailAlreadyUsed(EmailAlreadyUsedException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> duplicateDatabaseValue(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "Un compte utilise deja cet email");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> badCredentials(BadCredentialsException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> disabled(DisabledException exception) {
        return error(HttpStatus.FORBIDDEN, "Ce compte a ete suspendu");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " : " + error.getDefaultMessage())
                .orElse("Informations invalides");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> illegalArgument(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), message));
    }
}
