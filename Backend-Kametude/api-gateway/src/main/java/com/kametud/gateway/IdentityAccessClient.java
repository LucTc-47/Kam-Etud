package com.kametud.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Component
public class IdentityAccessClient implements UserAccessVerifier {
    private final WebClient webClient;
    private final String internalServiceToken;
    private final Duration timeout;

    public IdentityAccessClient(
            @Value("${identity.service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${internal.service-token:change-this-internal-token}") String internalServiceToken,
            @Value("${identity.service.access-timeout:2s}") Duration timeout) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.internalServiceToken = internalServiceToken;
        this.timeout = timeout;
    }

    @Override
    public Mono<Boolean> isEnabled(String userId) {
        return webClient.get()
                .uri("/internal/users/{userId}/access", userId)
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .onStatus(status -> status.value() == 404, response -> Mono.empty())
                .bodyToMono(AccessResponse.class)
                .map(AccessResponse::enabled)
                .defaultIfEmpty(false)
                .timeout(timeout);
    }

    private record AccessResponse(String userId, boolean enabled) {
    }
}
