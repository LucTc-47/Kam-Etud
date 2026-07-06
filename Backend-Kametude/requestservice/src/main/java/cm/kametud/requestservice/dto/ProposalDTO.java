package cm.kametud.requestservice.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProposalDTO {
    private UUID id;
    private UUID requestId;
    private UUID studentId;
    private String studentName;
    private String message;
    private Double price;
    private Integer deliveryDays;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String requestTitle;
    private Double requestBudget;
    private String requestStatus;
}
