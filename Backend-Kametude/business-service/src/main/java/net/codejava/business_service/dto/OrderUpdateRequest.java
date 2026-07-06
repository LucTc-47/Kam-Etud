package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderUpdateRequest {
    @NotBlank(message = "Le statut est obligatoire")
    private String status;
    private String deliverableUrl;
    private String deliverableNote;

    /* Ancien champ calcule par React. Le backend gere maintenant lui-meme le compteur. */
    private Integer revisionsLeft;
}
