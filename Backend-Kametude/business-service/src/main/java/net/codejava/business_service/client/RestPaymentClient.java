package net.codejava.business_service.client;

import net.codejava.business_service.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestPaymentClient implements PaymentClient {
    private final RestClient restClient;
    private final String internalServiceToken;

    public RestPaymentClient(RestClient.Builder builder,
                             @Value("${payment.service.base-url:http://localhost:8085}") String baseUrl,
                             @Value("${internal.service-token:change-this-internal-token}") String internalServiceToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalServiceToken = internalServiceToken;
    }

    @Override
    public void releaseAutomatically(UUID orderId) {
        try {
            restClient.post()
                    .uri("/api/payments/internal/order/{orderId}/release", orderId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment Service indisponible pour la liberation automatique");
        }
    }
}
