package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisputeResponseRequest {
    @NotBlank private String statement;
    private String evidenceUrl;
}
