package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class ProposalOrderRequest {
    @NotNull private UUID sourceRequestId;
    @NotNull private UUID sourceProposalId;
    @NotNull private UUID clientId;
    @NotBlank private String clientName;
    @NotNull private UUID studentId;
    @NotBlank private String studentName;
    @NotBlank private String title;
    private String description;
    @NotNull @Positive private Double budget;
    @NotNull @Positive private Integer deliveryDays;
}
