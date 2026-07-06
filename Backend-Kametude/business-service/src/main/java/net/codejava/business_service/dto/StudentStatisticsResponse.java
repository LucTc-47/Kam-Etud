package net.codejava.business_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StudentStatisticsResponse(
        @JsonProperty("completed_jobs") long completedJobs,
        @JsonProperty("review_count") long reviewCount,
        double rating,
        @JsonProperty("response_time") String responseTime,
        @JsonProperty("level_badge") String levelBadge,
        long xp,
        @JsonProperty("next_level_xp") long nextLevelXp) {
}
