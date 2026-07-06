package com.darwin.authservice.entity;

public enum Role {
    ADMIN,
    MODERATOR,
    USER,
    STUDENT,
    CLIENT;

    public String toLower() {
        return this.name().toLowerCase();
    }
}