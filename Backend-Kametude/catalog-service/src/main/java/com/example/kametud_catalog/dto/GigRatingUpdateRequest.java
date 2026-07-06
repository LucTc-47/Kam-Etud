package com.example.kametud_catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record GigRatingUpdateRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("5.00") BigDecimal rating,
        @PositiveOrZero int reviewCount
) {
}
