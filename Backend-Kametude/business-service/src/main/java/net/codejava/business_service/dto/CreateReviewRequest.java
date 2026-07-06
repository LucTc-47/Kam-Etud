package net.codejava.business_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateReviewRequest {
    @NotNull private UUID orderId;
    @Min(1) @Max(5) private int rating;
    private String text;

    /* Anciens champs gigId/studentId envoyes par React, conserves mais ignores. */
    private UUID gigId;
    private UUID studentId;
}
