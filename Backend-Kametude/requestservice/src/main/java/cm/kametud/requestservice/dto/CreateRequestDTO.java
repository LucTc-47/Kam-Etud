package cm.kametud.requestservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateRequestDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String description;

    @NotNull(message = "Le budget est obligatoire")
    @Positive(message = "Le budget doit être positif")
    private Double budget;

    @NotBlank(message = "La catégorie est obligatoire")
    private String category;

    private String location;

    private LocalDate deadline;

    /* Ancien contrat : @NotNull et valeurs fournies par React.
       Ces champs restent lisibles mais sont ignores; l'identite vient du JWT. */
    private UUID clientId;

    private String clientName;
}
