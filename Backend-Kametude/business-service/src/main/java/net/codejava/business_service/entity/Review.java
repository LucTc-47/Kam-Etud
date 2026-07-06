package net.codejava.business_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "reviewer_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    private UUID gigId;
    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;
    @Column(nullable = false)
    private String reviewerName;
    @Column(nullable = false)
    private UUID studentId;
    @Column(nullable = false)
    private Integer rating;
    @Column(columnDefinition = "TEXT")
    private String text;
    private boolean reported;
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hidden = false;
    @Column(columnDefinition = "TEXT")
    private String reportReason;
    private LocalDateTime reportedAt;
    private UUID moderatedBy;
    private LocalDateTime moderatedAt;
    @Column(columnDefinition = "TEXT")
    private String moderationNote;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
