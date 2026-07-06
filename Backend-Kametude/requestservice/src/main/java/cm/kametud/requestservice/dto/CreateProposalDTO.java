package cm.kametud.requestservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateProposalDTO {

    @NotNull(message = "L'ID de la demande est obligatoire")
    private UUID requestId;

    /* Anciens champs envoyes par le navigateur, conserves mais ignores. */
    private UUID studentId;

    private String studentName;

    private String message;

    @NotNull(message = "Le prix est obligatoire")
    @Positive(message = "Le prix doit être positif")
    private Double price;

    @NotNull(message = "Le délai de livraison est obligatoire")
    @Positive(message = "Le délai doit être positif")
    private Integer deliveryDays;
}
