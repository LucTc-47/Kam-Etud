package com.darwin.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private String token;

    @JsonProperty("refresh_token")
    private String refreshToken;

    private String email;
    private String role;

    private ProfileResponse profile;
}