package com.kametude.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class RestSupportClient implements SupportClient {
    private final RestClient restClient;
    private final String token;
    public RestSupportClient(RestClient.Builder builder,
                             @Value("${support.service.base-url:http://localhost:8086}") String baseUrl,
                             @Value("${internal.service-token:change-this-internal-token}") String token) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.token = token;
    }
    public void notify(UUID userId, String title, String message, String type, String link) {
        try {
            restClient.post().uri("/api/notifications/internal")
                    .header("X-Internal-Service-Token", token)
                    .body(Map.of("userId", userId, "title", title, "message", message, "type", type, "link", link))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException ignored) {
            // Un echec de notification ne doit pas dupliquer un payout.
        }
    }
}
