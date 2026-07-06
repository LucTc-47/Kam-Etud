package net.codejava.business_service.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProfileSummary(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName
) {
    public String displayName() {
        String name = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();
        return name.isBlank() ? "Client" : name;
    }
}
