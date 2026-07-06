package com.kametude.payment.client;

import com.kametude.payment.exception.PaymentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestBusinessClient implements BusinessClient {
    private final RestClient restClient;
    private final String internalToken;

    public RestBusinessClient(RestClient.Builder builder,
                              @Value("${business.service.base-url:http://localhost:8084}") String baseUrl,
                              @Value("${internal.service-token:change-this-internal-token}") String internalToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public BusinessOrderContext getOrder(UUID orderId) {
        try {
            BusinessOrderContext order = restClient.get().uri("/api/orders/internal/{id}", orderId)
                    .header("X-Internal-Service-Token", internalToken)
                    .retrieve().body(BusinessOrderContext.class);
            if (order == null) throw new PaymentException("BUSINESS_UNAVAILABLE", "Commande Business vide");
            return order;
        } catch (RestClientException exception) {
            throw new PaymentException("BUSINESS_UNAVAILABLE", "Business Service indisponible");
        }
    }

    @Override
    public void markPaymentHeld(UUID orderId) {
        try {
            restClient.post().uri("/api/orders/internal/{id}/payment-held", orderId)
                    .header("X-Internal-Service-Token", internalToken)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new PaymentException("BUSINESS_UNAVAILABLE", "Synchronisation Business indisponible");
        }
    }
}
