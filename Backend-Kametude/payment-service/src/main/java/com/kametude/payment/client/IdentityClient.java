package com.kametude.payment.client;

import java.util.UUID;

public interface IdentityClient {
    IdentityProfile getPayoutProfile(UUID userId);
}
