package com.darwin.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateVerificationRequest {
    @NotBlank
    private String status;
}
