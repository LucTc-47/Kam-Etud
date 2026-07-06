package com.example.kametud_catalog.exception;

public class CatalogAccessDeniedException extends RuntimeException {
    public CatalogAccessDeniedException(String message) {
        super(message);
    }
}
