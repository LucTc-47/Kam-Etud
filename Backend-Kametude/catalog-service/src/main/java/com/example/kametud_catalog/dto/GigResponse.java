package com.example.kametud_catalog.dto;

import com.example.kametud_catalog.entity.Gig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GigResponse {

    private UUID id;
    private UUID studentId;
    private String studentName;
    private String title;
    private String description;
    private String category;
    private String location;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer orderCount;
    private String badge;
    private List<String> images;
    private boolean active;
    private Double gpsLat;
    private Double gpsLng;
    private GigTierDto tierBasique;
    private GigTierDto tierStandard;
    private GigTierDto tierPremium;
    private boolean published;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static GigResponse fromEntity(Gig gig) {
        return GigResponse.builder()
                .id(gig.getId())
                .studentId(gig.getStudentId())
                .studentName(gig.getStudentName() == null ? "Etudiant" : gig.getStudentName())
                .title(gig.getTitle())
                .description(gig.getDescription())
                .category(gig.getCategory())
                .location(gig.getLocation())
                .rating(gig.getRating())
                .reviewCount(gig.getReviewCount())
                .orderCount(gig.getOrderCount())
                .badge(gig.getBadge())
                .images(gig.getImages() == null ? List.of() : List.copyOf(gig.getImages()))
                .active(gig.isActive())
                .gpsLat(gig.getGpsLat())
                .gpsLng(gig.getGpsLng())
                .tierBasique(gig.getTierBasique())
                .tierStandard(gig.getTierStandard())
                .tierPremium(gig.getTierPremium())
                .published(gig.isPublished())
                .createdAt(gig.getCreatedAt())
                .updatedAt(gig.getUpdatedAt())
                .build();
    }
}
