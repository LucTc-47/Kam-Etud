package com.example.kametud_catalog.dto;

import jakarta.validation.constraints.NotNull;

public record PublicationRequest(@NotNull Boolean published) {
}
