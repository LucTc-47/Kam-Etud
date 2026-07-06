package net.codejava.business_service.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private UUID id;
    private UUID clientId;
    private String clientName;
    private UUID studentId;
    private String studentName;
    private UUID gigId;
    private UUID sourceRequestId;
    private UUID sourceProposalId;
    private String gigTitle;
    private String tier;
    private String description;
    private Double budget;
    private String status;
    private Integer revisionsLeft;
    private LocalDateTime deliveryDate;
    private Double escrowAmount;
    private String paymentMethod;
    private String deliverableUrl;
    private String deliverableNote;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
