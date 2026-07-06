package com.darwin.authservice.dto;

import java.util.UUID;

public record InternalAccessResponse(UUID userId, boolean enabled) {
}
