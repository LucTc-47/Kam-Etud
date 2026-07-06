package com.darwin.authservice.client;

// Ancien import Lombok devenu inutile : le constructeur est explicite pour injecter les URLs.
// import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
// Ancien import retire du code actif : aucune reponse HTTP n'est construite dans ce client.
// import org.springframework.http.HttpStatus;
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
    public int deactivateStudentGigs(UUID studentId) {
        try {
            GigDeactivationResponse response = restClient.patch()
                    .uri("/api/gigs/internal/students/{studentId}/deactivate", studentId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .body(GigDeactivationResponse.class);
            return response == null ? 0 : response.deactivatedCount();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Catalog Service indisponible : bannissement non applique", exception);
        }
    }

    private record GigDeactivationResponse(int deactivatedCount) {
    }
}
