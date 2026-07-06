package net.codejava.business_service.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionResponse {
    private UUID id;
    private UUID orderId;
    private String reason;
    private LocalDateTime requestedAt;
}
