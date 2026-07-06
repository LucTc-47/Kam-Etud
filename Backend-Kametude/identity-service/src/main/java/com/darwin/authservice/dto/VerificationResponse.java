package com.darwin.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VerificationResponse {
    private UUID id;

    @JsonProperty("student_id")
    private UUID studentId;

    @JsonProperty("student_name")
    private String studentName;

    private String email;
    private String university;

    @JsonProperty("id_type")
    private String idType;

    @JsonProperty("id_file_url")
    private String idFileUrl;

    @JsonProperty("selfie_url")
    private String selfieUrl;

    @JsonProperty("student_card_url")
    private String studentCardUrl;

    private String status;

    @JsonProperty("submitted_at")
    private Instant submittedAt;

    @JsonProperty("reviewed_at")
    private Instant reviewedAt;
}
