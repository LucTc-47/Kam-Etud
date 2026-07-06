package net.codejava.business_service.entity;

import jakarta.persistence.*;
import lombok.*;
import net.codejava.business_service.enums.DisputeStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "disputes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Dispute {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private UUID orderId;
    @Column(nullable = false)
    private String gigTitle;
    @Column(nullable = false)
    private UUID clientId;
    @Column(nullable = false)
    private String clientName;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String clientStatement;
    private String clientEvidenceUrl;
    @Column(nullable = false)
    private UUID studentId;
    @Column(nullable = false)
    private String studentName;
    @Column(columnDefinition = "TEXT")
    private String studentStatement;
    private String studentEvidenceUrl;
    @Enumerated(EnumType.STRING)
    private DisputeStatus status;
    private UUID moderatorId;
    @Column(columnDefinition = "TEXT")
    private String moderatorNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = DisputeStatus.OPEN;
        createdAt = LocalDateTime.now();
    }
}
