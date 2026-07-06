package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionRequest {

    @NotNull(message = "orderId est obligatoire")
    private UUID orderId;

    @NotNull(message = "La raison est obligatoire")
    private String reason;
}
