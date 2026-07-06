package cm.kametud.requestservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProfileSummary(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName
) {
    public String displayName() {
        String value = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();
        return value.isBlank() ? "Utilisateur" : value;
    }
}
