package com.kametude.support_service.client;

import java.util.UUID;

public interface BusinessClient {
    BusinessOrderContext getOrder(UUID orderId);
}
