package net.codejava.business_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class ReviewResponse {
    private UUID id;
    private UUID orderId;
    private UUID gigId;
    private UUID reviewerId;
    private String reviewerName;
    private UUID studentId;
    private Integer rating;
    private String text;
    private boolean reported;
    private LocalDateTime createdAt;
}
