package cm.kametud.requestservice.model;

import cm.kametud.requestservice.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gig_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GigRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double budget;

    @Column(nullable = false)
    private String category;

    private String location;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.OUVERT;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String clientName;

    @Column(name = "accepted_proposal_id")
    private UUID acceptedProposalId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
