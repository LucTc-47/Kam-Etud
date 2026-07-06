package com.darwin.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String email;

    private String university;

    @Column(name = "id_type", nullable = false)
    private String idType;

    @Column(name = "id_file_url", nullable = false, columnDefinition = "TEXT")
    private String idFileUrl;

    @Column(name = "selfie_url", nullable = false, columnDefinition = "TEXT")
    private String selfieUrl;

    @Column(name = "student_card_url", columnDefinition = "TEXT")
    private String studentCardUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
