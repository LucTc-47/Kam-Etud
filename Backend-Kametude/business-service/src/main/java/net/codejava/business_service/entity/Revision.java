package net.codejava.business_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Revision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID orderId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private LocalDateTime requestedAt;

    @PrePersist
    public void prePersist() {
        requestedAt = LocalDateTime.now();
    }
}
