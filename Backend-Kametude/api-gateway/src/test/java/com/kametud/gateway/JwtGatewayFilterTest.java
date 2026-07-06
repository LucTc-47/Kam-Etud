package com.kametud.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtGatewayFilterTest {

    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final JwtGatewayFilter filter = new JwtGatewayFilter(SECRET);

    @Test
    void rejectsProtectedRouteWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/profiles/me").build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void propagatesTrustedIdentityHeadersForValidAccessToken() {
        String userId = UUID.randomUUID().toString();
        String token = token(userId, "STUDENT", "access");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Role", "ADMIN")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");
    }

    @Test
    void refusesAdminRouteForStudentToken() {
        String token = token(UUID.randomUUID().toString(), "STUDENT", "access");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsAnAlreadyIssuedTokenWhenIdentityDisablesTheUser() {
        String token = token(UUID.randomUUID().toString(), "STUDENT", "access");
        JwtGatewayFilter disabledUserFilter = new JwtGatewayFilter(SECRET, userId -> Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/profiles/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        disabledUserFilter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void protectsCatalogMutations() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/gigs").build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refusesCategoryMutationForStudentToken() {
        String token = token(UUID.randomUUID().toString(), "STUDENT", "access");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void leavesPublicRequestReadsAccessibleWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/requests").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectsRequestMutationWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/requests").build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refusesRequestCreationForStudentToken() {
        String token = token(UUID.randomUUID().toString(), "STUDENT", "access");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void forwardsProposalCreationOnlyForStudentToken() {
        String userId = UUID.randomUUID().toString();
        String token = token(userId, "STUDENT", "access");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/proposals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId);
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");
    }

    @Test
    void refusesProposalAcceptanceForStudentToken() {
        String token = token(UUID.randomUUID().toString(), "STUDENT", "access");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/v1/proposals/" + UUID.randomUUID() + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void protectsOrdersAndAppliesBusinessRoles() {
        MockServerWebExchange noToken = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/mine").build());
        filter.filter(noToken, ignored -> Mono.empty()).block();
        assertThat(noToken.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String studentToken = token(UUID.randomUUID().toString(), "STUDENT", "access");
        MockServerWebExchange studentCreatingOrder = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken).build());
        filter.filter(studentCreatingOrder, ignored -> Mono.empty()).block();
        assertThat(studentCreatingOrder.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String clientToken = token(UUID.randomUUID().toString(), "CLIENT", "access");
        MockServerWebExchange clientReadingDisputes = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/disputes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken).build());
        filter.filter(clientReadingDisputes, ignored -> Mono.empty()).block();
        assertThat(clientReadingDisputes.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void restrictsAbuseReportsToModeratorAndAdminResponsibilities() {
        String studentToken = token(UUID.randomUUID().toString(), "STUDENT", "access");
        MockServerWebExchange studentCreatingReport = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/abuse-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken).build());
        filter.filter(studentCreatingReport, ignored -> Mono.empty()).block();
        assertThat(studentCreatingReport.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String moderatorToken = token(UUID.randomUUID().toString(), "MODERATOR", "access");
        AtomicReference<ServerWebExchange> moderatorForwarded = new AtomicReference<>();
        MockServerWebExchange moderatorCreatingReport = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/abuse-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken).build());
        filter.filter(moderatorCreatingReport, current -> {
            moderatorForwarded.set(current);
            return Mono.empty();
        }).block();
        assertThat(moderatorForwarded.get()).isNotNull();
        assertThat(moderatorForwarded.get().getRequest().getHeaders().getFirst("X-User-Role"))
                .isEqualTo("MODERATOR");

        MockServerWebExchange moderatorReadingAdminQueue = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/abuse-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken).build());
        filter.filter(moderatorReadingAdminQueue, ignored -> Mono.empty()).block();
        assertThat(moderatorReadingAdminQueue.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String adminToken = token(UUID.randomUUID().toString(), "ADMIN", "access");
        AtomicReference<ServerWebExchange> adminForwarded = new AtomicReference<>();
        MockServerWebExchange adminReadingQueue = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/abuse-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).build());
        filter.filter(adminReadingQueue, current -> {
            adminForwarded.set(current);
            return Mono.empty();
        }).block();
        assertThat(adminForwarded.get()).isNotNull();
        assertThat(adminForwarded.get().getRequest().getHeaders().getFirst("X-User-Role"))
                .isEqualTo("ADMIN");
    }

    @Test
    void leavesStudentReviewsPublic() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/reviews/student/" + UUID.randomUUID()).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, current -> { forwarded.set(current); return Mono.empty(); }).block();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void protectsPaymentAndRestrictsInitiationToClient() {
        MockServerWebExchange noToken = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/payments/order/" + UUID.randomUUID()).build());
        filter.filter(noToken, ignored -> Mono.empty()).block();
        assertThat(noToken.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String studentToken = token(UUID.randomUUID().toString(), "STUDENT", "access");
        MockServerWebExchange studentInitiation = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/payments/initiate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken).build());
        filter.filter(studentInitiation, ignored -> Mono.empty()).block();
        assertThat(studentInitiation.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void protectsSupportDataButAllowsPublicAvatars() {
        MockServerWebExchange chat = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chat/orders/" + UUID.randomUUID() + "/messages").build());
        filter.filter(chat, ignored -> Mono.empty()).block();
        assertThat(chat.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MockServerWebExchange privateFile = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/storage/private/files/file.pdf").build());
        filter.filter(privateFile, ignored -> Mono.empty()).block();
        assertThat(privateFile.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        MockServerWebExchange publicFile = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/storage/files/avatar.png").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(publicFile, current -> { forwarded.set(current); return Mono.empty(); }).block();
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void neverExposesInternalServiceRoutesThroughTheGateway() {
        String adminToken = token(UUID.randomUUID().toString(), "ADMIN", "access");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/orders/internal/" + UUID.randomUUID() + "/payment-held")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header("X-Internal-Service-Token", "forged")
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String token(String userId, String role, String tokenType) {
        return Jwts.builder()
                .subject("student@example.com")
                .claim("userId", userId)
                .claim("role", role)
                .claim("tokenType", tokenType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();
    }
}
