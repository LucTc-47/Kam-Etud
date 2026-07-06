package cm.kametud.requestservice.client;

import cm.kametud.requestservice.exception.RequestDomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
    public void createOrder(ProposalOrderCommand command) {
        try {
            restClient.post().uri("/api/orders/internal/from-proposal")
                    .header("X-Internal-Service-Token", internalToken)
                    .body(command).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new RequestDomainException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Business Service indisponible : la proposition n'a pas ete acceptee");
        }
    }
}
