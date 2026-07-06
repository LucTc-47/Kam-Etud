package com.example.kametud_catalog.dto;

import java.util.UUID;

public record CatalogOptionResponse(UUID id, String name, boolean active) {
}
