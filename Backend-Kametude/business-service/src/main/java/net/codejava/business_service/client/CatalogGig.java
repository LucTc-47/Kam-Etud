package net.codejava.business_service.client;

import java.util.UUID;

public record CatalogGig(
        UUID id,
        UUID studentId,
        String studentName,
        String title,
        String description,
        boolean active,
        boolean published,
        CatalogTier tierBasique,
        CatalogTier tierStandard,
        CatalogTier tierPremium
) {
    public CatalogTier tier(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "basique" -> tierBasique;
            case "standard" -> tierStandard;
            case "premium" -> tierPremium;
            default -> null;
        };
    }
}
