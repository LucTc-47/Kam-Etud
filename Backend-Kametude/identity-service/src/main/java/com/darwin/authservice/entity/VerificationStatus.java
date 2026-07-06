package com.darwin.authservice.entity;

public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public String toLower() {
        return name().toLowerCase();
    }
}
