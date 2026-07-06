package net.codejava.business_service.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliverableResponse {
    private UUID id;
    private UUID orderId;
    private String fileUrl;
    private String description;
    private LocalDateTime submittedAt;
}
