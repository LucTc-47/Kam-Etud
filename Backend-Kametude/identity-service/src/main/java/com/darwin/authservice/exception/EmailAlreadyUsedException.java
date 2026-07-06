package com.darwin.authservice.exception;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("Cet email est deja utilise : " + email);
    }
}
