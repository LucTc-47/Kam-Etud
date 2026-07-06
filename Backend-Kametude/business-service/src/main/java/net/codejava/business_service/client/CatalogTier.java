package net.codejava.business_service.client;

import java.math.BigDecimal;

public record CatalogTier(String name, String description, BigDecimal price, Integer deliveryDays) {
}
