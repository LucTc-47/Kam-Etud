package com.kametude.support_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StoredFile {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String filename;
    @Column(nullable = false)
    private UUID ownerId;
    @Column(nullable = false)
    private boolean privateFile;
    private String category;
    private UUID resourceId;
    private Instant createdAt;

    @PrePersist
    void prePersist() { createdAt = Instant.now(); }
}
