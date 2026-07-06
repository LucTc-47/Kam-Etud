package net.codejava.business_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class AbuseReportResponse {
    private UUID id;
    private UUID disputeId;
    private UUID orderId;
    private String gigTitle;
    private UUID targetUserId;
    private String targetName;
    private String targetRole;
    private UUID moderatorId;
    private String reason;
    private String note;
    private String status;
    private String clientStatement;
    private String clientEvidenceUrl;
    private String studentStatement;
    private String studentEvidenceUrl;
    private UUID adminId;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
