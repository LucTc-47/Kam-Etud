package net.codejava.business_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliverableRequest {

    @NotNull(message = "orderId est obligatoire")
    private UUID orderId;

    private String fileUrl;

    private String description;
}
