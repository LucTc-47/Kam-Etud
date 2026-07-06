package net.codejava.business_service.client;

import net.codejava.business_service.exception.BusinessException;
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
                              @Value("${identity.service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public ProfileSummary getProfile(UUID userId) {
        try {
            ProfileSummary profile = restClient.get().uri("/api/profiles/{id}", userId)
                    .retrieve().body(ProfileSummary.class);
            if (profile == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Profil Identity vide");
            return profile;
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Identity Service indisponible");
        }
    }
}
