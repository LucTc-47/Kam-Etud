package com.example.kametud_catalog.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StudentProfileSummary(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName
) {
    public String displayName() {
        String value = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();
        return value.isBlank() ? "Etudiant" : value;
    }
}
