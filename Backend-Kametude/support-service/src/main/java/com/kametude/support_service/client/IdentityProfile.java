package com.kametude.support_service.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IdentityProfile(@JsonProperty("first_name") String firstName,
                              @JsonProperty("last_name") String lastName) {
    public String displayName() {
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return name.isBlank() ? "Utilisateur" : name;
    }
}
