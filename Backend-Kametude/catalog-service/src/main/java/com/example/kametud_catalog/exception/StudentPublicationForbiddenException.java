package com.example.kametud_catalog.exception;

import java.util.UUID;

public class StudentPublicationForbiddenException extends RuntimeException {

    public StudentPublicationForbiddenException(UUID studentId) {
        super("Student cannot publish gigs: " + studentId);
    }
}
