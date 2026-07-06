package com.kametude.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "seller_phone", length = 20)
    private String sellerPhone;

    @Column(name = "payout_reference")
    private String payoutReference;

    @Column(name = "commission")
    private BigDecimal commission;

    // nullable reste vrai pour migrer sans casser les transactions historiques ;
    // toute nouvelle reservation renseigne obligatoirement cette cle.
    @Column(name = "collection_key", unique = true)
    private String collectionKey;

    @Column(name = "payout_key", unique = true)
    private String payoutKey;

    @Builder.Default
    @Column(name = "collection_attempt", nullable = false, columnDefinition = "integer default 1")
    private Integer collectionAttempt = 1;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
