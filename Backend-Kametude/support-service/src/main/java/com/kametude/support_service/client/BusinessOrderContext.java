package com.kametude.support_service.client;

import java.util.UUID;

public record BusinessOrderContext(UUID id, UUID clientId, UUID studentId, String gigTitle, String status) {
    public boolean hasParticipant(UUID userId) {
        return clientId.equals(userId) || studentId.equals(userId);
    }

    public UUID otherParticipant(UUID userId) {
        return clientId.equals(userId) ? studentId : clientId;
    }
}
