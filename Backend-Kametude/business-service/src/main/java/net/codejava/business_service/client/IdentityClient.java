package net.codejava.business_service.client;

import java.util.UUID;

public interface IdentityClient {
    ProfileSummary getProfile(UUID userId);
}
