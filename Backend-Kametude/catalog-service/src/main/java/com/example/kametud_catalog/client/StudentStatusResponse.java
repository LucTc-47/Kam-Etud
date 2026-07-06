package com.example.kametud_catalog.client;

public record StudentStatusResponse(Boolean verified, Boolean banned) {

    public boolean canPublish() {
        return Boolean.TRUE.equals(verified) && !Boolean.TRUE.equals(banned);
    }
}
