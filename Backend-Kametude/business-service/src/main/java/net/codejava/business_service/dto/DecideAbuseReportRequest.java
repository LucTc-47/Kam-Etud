package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DecideAbuseReportRequest {
    @NotBlank
    private String action;
    @NotBlank
    @Size(max = 2000)
    private String adminNote;
}
