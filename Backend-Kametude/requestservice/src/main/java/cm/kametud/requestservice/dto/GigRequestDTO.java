package cm.kametud.requestservice.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class GigRequestDTO {
    private UUID id;
    private String title;
    private String description;
    private Double budget;
    private String category;
    private String location;
    private LocalDateTime deadline;
    private String status;
    private UUID clientId;
    private String clientName;
    private UUID acceptedProposalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer proposalsCount;
}
