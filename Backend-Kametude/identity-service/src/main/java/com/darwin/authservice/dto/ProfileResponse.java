package com.darwin.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileResponse {

    private UUID id;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String email;
    private String phone;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String bio;
    private String city;
    private String university;
    private String faculty;
    private String level;
    private List<String> skills;
    private Float rating;
    private String role;
    private Boolean verified;
    private Boolean banned;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
