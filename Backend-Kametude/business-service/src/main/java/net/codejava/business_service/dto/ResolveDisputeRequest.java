package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveDisputeRequest {
    @NotBlank private String status;
    private String moderatorNote;
}
