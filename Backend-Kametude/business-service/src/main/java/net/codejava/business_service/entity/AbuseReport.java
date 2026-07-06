package net.codejava.business_service.entity;

import jakarta.persistence.*;
import lombok.*;
import net.codejava.business_service.enums.AbuseReportReason;
import net.codejava.business_service.enums.AbuseReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "abuse_reports", indexes = {
        @Index(name = "idx_abuse_report_status", columnList = "status"),
        @Index(name = "idx_abuse_report_dispute_target", columnList = "dispute_id,target_user_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AbuseReport {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Column(nullable = false)
    private UUID targetUserId;
    @Column(nullable = false)
    private String targetName;
    @Column(nullable = false)
    private String targetRole;
    @Column(nullable = false)
    private UUID moderatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AbuseReportReason reason;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AbuseReportStatus status;
    private UUID adminId;
    @Column(columnDefinition = "TEXT")
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = AbuseReportStatus.OPEN;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
