package com.kametude.support_service.security;

import java.security.Principal;
import java.util.UUID;


public class StompPrincipal implements Principal {

    private final UUID userId;
    private final String username;
    private final String role;

    public StompPrincipal(UUID userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

   
    @Override
    public String getName() {
        return userId.toString();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}