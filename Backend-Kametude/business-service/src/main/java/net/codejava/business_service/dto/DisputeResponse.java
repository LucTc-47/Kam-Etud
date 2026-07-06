package net.codejava.business_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class DisputeResponse {
    private UUID id;
    private UUID orderId;
    private String gigTitle;
    private UUID clientId;
    private String clientName;
    private String clientStatement;
    private String clientEvidenceUrl;
    private UUID studentId;
    private String studentName;
    private String studentStatement;
    private String studentEvidenceUrl;
    private String status;
    private UUID moderatorId;
    private String moderatorNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
