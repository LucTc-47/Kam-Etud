package com.kametude.support_service.client;

import java.util.UUID;

public interface IdentityClient {
    IdentityProfile getProfile(UUID userId);
}
