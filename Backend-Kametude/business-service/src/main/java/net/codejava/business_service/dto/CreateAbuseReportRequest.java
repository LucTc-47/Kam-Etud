package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAbuseReportRequest {
    @NotNull
    private UUID disputeId;
    @NotNull
    private UUID targetUserId;
    @NotBlank
    private String reason;
    @NotBlank
    @Size(min = 10, max = 2000)
    private String note;
}
