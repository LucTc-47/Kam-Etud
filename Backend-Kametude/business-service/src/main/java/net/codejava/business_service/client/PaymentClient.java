package net.codejava.business_service.client;

import java.util.UUID;

public interface PaymentClient {
    void releaseAutomatically(UUID orderId);
}
