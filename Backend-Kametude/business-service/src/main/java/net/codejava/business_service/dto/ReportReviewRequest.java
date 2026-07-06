package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportReviewRequest(
        @NotBlank @Size(max = 500) String reason) {
}
