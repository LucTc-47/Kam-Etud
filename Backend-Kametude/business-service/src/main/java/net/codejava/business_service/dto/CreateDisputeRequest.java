package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateDisputeRequest {
    @NotNull private UUID orderId;
    @NotBlank private String clientStatement;
    private String clientEvidenceUrl;

    /* Anciens champs React conserves mais ignores : les donnees viennent de la commande. */
    private String gigTitle;
    private UUID studentId;
    private String studentName;
}
