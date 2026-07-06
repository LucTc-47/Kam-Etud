package net.codejava.business_service.client;

import net.codejava.business_service.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestCatalogClient implements CatalogClient {
    private final RestClient restClient;
    private final String internalServiceToken;

    public RestCatalogClient(RestClient.Builder builder,
                             @Value("${catalog.service.base-url:http://localhost:8083}") String baseUrl,
                             @Value("${internal.service-token:change-this-internal-token}") String internalServiceToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalServiceToken = internalServiceToken;
    }

    @Override
    public CatalogGig getGig(UUID gigId) {
        try {
            CatalogGig gig = restClient.get().uri("/api/gigs/{id}", gigId)
                    .retrieve().body(CatalogGig.class);
            if (gig == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Reponse Catalog vide");
            return gig;
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog Service indisponible");
        }
    }

    @Override
    public void updateRating(UUID gigId, double rating, long reviewCount) {
        try {
            restClient.patch()
                    .uri("/api/gigs/internal/{id}/rating", gigId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .body(new RatingUpdateRequest(rating, reviewCount))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Synchronisation de la note Catalog indisponible");
        }
    }

    private record RatingUpdateRequest(double rating, long reviewCount) {
    }
}
