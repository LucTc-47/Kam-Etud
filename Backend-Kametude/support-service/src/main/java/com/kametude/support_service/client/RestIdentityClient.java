package com.kametude.support_service.client;

import com.kametude.support_service.exception.SupportException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestIdentityClient implements IdentityClient {
    private final RestClient restClient;

    public RestIdentityClient(RestClient.Builder builder,
                              @Value("${services.identity-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public IdentityProfile getProfile(UUID userId) {
        try {
            IdentityProfile profile = restClient.get().uri("/api/profiles/{id}", userId)
                    .retrieve().body(IdentityProfile.class);
            if (profile == null) throw new SupportException(HttpStatus.SERVICE_UNAVAILABLE, "Profil Identity vide");
            return profile;
        } catch (RestClientException exception) {
            throw new SupportException(HttpStatus.SERVICE_UNAVAILABLE, "Identity Service indisponible");
        }
    }
}
