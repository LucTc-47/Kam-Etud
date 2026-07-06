package net.codejava.business_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

import net.codejava.business_service.enums.OrderStatus;

@Entity
// Ancienne table : @Table(name = "orders") avec des identifiants Long.
// Une nouvelle table evite de tenter de convertir une cle bigint existante en UUID.
@Table(name = "business_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String clientName;

    @Column(nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private String studentName;

    private UUID gigId;

    private UUID sourceRequestId;

    @Column(unique = true)
    private UUID sourceProposalId;

    @Column(nullable = false)
    private String gigTitle;

    @Column(nullable = false)
    private String tier;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double budget;

    @Column(nullable = false)
    private Double escrowAmount;

    private String paymentMethod;

    private LocalDateTime deliveryDate;

    private LocalDateTime acceptedAt;

    private Integer revisionsLeft;

    private String deliverableUrl;

    @Column(columnDefinition = "TEXT")
    private String deliverableNote;

    private LocalDateTime deliveredAt;

    private LocalDateTime autoValidatedAt;

    private LocalDateTime payoutReleasedAt;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = OrderStatus.PENDING;
        if (revisionsLeft == null) revisionsLeft = 2;
        if (escrowAmount == null) escrowAmount = budget;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
