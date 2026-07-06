package com.kametude.support_service.client;

import com.kametude.support_service.exception.SupportException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestBusinessClient implements BusinessClient {
    private final RestClient restClient;
    private final String internalToken;

    public RestBusinessClient(RestClient.Builder builder,
                              @Value("${services.business-url:http://localhost:8084}") String baseUrl,
                              @Value("${services.internal-token:change-this-internal-token}") String token) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalToken = token;
    }

    @Override
    public BusinessOrderContext getOrder(UUID orderId) {
        try {
            BusinessOrderContext order = restClient.get().uri("/api/orders/internal/{id}", orderId)
                    .header("X-Internal-Service-Token", internalToken).retrieve().body(BusinessOrderContext.class);
            if (order == null) throw new SupportException(HttpStatus.SERVICE_UNAVAILABLE, "Commande Business vide");
            return order;
        } catch (RestClientException exception) {
            throw new SupportException(HttpStatus.SERVICE_UNAVAILABLE, "Business Service indisponible");
        }
    }
}
