package com.kametude.payment.client;

import com.kametude.payment.exception.PaymentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestIdentityClient implements IdentityClient {
    private final RestClient restClient;
    private final String internalServiceToken;

    public RestIdentityClient(RestClient.Builder builder,
                              @Value("${identity.service.base-url:http://localhost:8081}") String baseUrl,
                              @Value("${internal.service-token:change-this-internal-token}") String internalServiceToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalServiceToken = internalServiceToken;
    }

    @Override
    public IdentityProfile getPayoutProfile(UUID userId) {
        try {
            IdentityProfile profile = restClient.get()
                    // Ancien appel public : /api/profiles/{id}. Identity y masque
                    // volontairement le telephone et Payment recevait donc null.
                    .uri("/internal/users/{id}/payout-profile", userId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve().body(IdentityProfile.class);
            if (profile == null) throw new PaymentException("IDENTITY_UNAVAILABLE", "Profil Identity vide");
            return profile;
        } catch (RestClientException exception) {
            throw new PaymentException("IDENTITY_UNAVAILABLE", "Identity Service indisponible");
        }
    }
}
