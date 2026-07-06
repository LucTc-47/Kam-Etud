package com.darwin.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateVerificationRequest {

    @NotBlank
    private String university;

    @NotBlank
    @JsonProperty("id_type")
    private String idType;

    @NotBlank
    @JsonProperty("id_file_url")
    private String idFileUrl;

    @NotBlank
    @JsonProperty("selfie_url")
    private String selfieUrl;

    @JsonProperty("student_card_url")
    private String studentCardUrl;
}
