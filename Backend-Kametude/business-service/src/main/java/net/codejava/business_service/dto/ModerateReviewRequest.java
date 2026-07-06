package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerateReviewRequest(
        @NotBlank String action,
        @Size(max = 1000) String note) {
}
