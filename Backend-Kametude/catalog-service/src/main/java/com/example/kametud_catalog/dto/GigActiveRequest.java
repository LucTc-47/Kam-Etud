package com.example.kametud_catalog.dto;

import jakarta.validation.constraints.NotNull;

public record GigActiveRequest(@NotNull Boolean active) {
}
