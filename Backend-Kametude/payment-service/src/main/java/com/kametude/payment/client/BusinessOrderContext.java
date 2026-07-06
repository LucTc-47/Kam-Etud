package com.kametude.payment.client;

import java.util.UUID;

public record BusinessOrderContext(
        UUID id, UUID clientId, UUID studentId, Double budget, String status
) {
}
