package net.codejava.business_service.client;

import java.util.UUID;

public interface CatalogClient {
    CatalogGig getGig(UUID gigId);
    void updateRating(UUID gigId, double rating, long reviewCount);
}
