package com.example.kametud_catalog.exception;

import java.util.UUID;

public class GigNotFoundException extends RuntimeException {

    public GigNotFoundException(UUID gigId) {
        super("Gig not found: " + gigId);
    }
}
