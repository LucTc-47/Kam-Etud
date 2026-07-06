package com.kametude.payment.client;

import java.util.UUID;

public interface BusinessClient {
    BusinessOrderContext getOrder(UUID orderId);
    void markPaymentHeld(UUID orderId);
}
