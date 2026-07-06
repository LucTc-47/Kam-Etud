package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    @NotNull(message = "gigId est obligatoire")
    private UUID gigId;

    @NotBlank(message = "Le palier est obligatoire")
    private String tier;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotBlank(message = "Le moyen de paiement est obligatoire")
    private String paymentMethod;

    /* Ancien contrat : clientId, studentId, noms, titre, budget et date etaient
       envoyes par React. Ils sont derives du JWT et du Catalog Service. */
    private UUID clientId;
    private UUID studentId;
    private String clientName;
    private String studentName;
    private String gigTitle;
    private Double budget;
}
