package com.kametud.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final SecretKey signingKey;
    private final UserAccessVerifier userAccessVerifier;

    @Autowired
    public JwtGatewayFilter(@Value("${jwt.secret}") String secret,
                            UserAccessVerifier userAccessVerifier) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.userAccessVerifier = userAccessVerifier;
    }

    JwtGatewayFilter(String secret) {
        this(secret, userId -> Mono.just(true));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        boolean catalogMutation = path.startsWith("/api/gigs") && method != HttpMethod.GET;
        boolean catalogAdminMutation = (path.startsWith("/api/categories") || path.startsWith("/api/cities"))
                && method != HttpMethod.GET;
        boolean proposalPath = path.startsWith("/api/v1/proposals");
        boolean proposalStudentPath = proposalPath
                && (method == HttpMethod.POST || method == HttpMethod.DELETE || path.equals("/api/v1/proposals/mine"));
        boolean proposalClientPath = proposalPath && method == HttpMethod.PUT;
        boolean requestClientPath = path.equals("/api/v1/requests/mine")
                || (path.startsWith("/api/v1/requests") && method != HttpMethod.GET);
        boolean businessClientPath = (path.equals("/api/orders") && method == HttpMethod.POST)
                || path.equals("/api/orders/mine")
                || (path.equals("/api/disputes") && method == HttpMethod.POST)
                || (path.startsWith("/api/revisions") && method == HttpMethod.POST)
                || (path.equals("/api/reviews") && method == HttpMethod.POST);
        boolean paymentClientPath = path.equals("/api/payments/initiate") && method == HttpMethod.POST;
        boolean businessStudentPath = path.equals("/api/orders/missions")
                || (path.startsWith("/api/deliverables") && method == HttpMethod.POST)
                || (path.startsWith("/api/disputes/order/") && path.endsWith("/response"))
                || (path.startsWith("/api/reviews/") && path.endsWith("/report"));
        boolean businessModeratorPath = path.equals("/api/abuse-reports") && method == HttpMethod.POST;
        boolean businessStaffPath = path.equals("/api/orders/all")
                || path.equals("/api/orders/auto-validation/run")
                || path.equals("/api/reviews/reported")
                || (path.startsWith("/api/reviews/") && path.endsWith("/moderate"))
                || (path.startsWith("/api/disputes") && method != HttpMethod.POST
                    && !(path.startsWith("/api/disputes/order/") && path.endsWith("/response")));
        boolean studentPath = catalogMutation || path.equals("/api/gigs/mine")
                || proposalStudentPath || businessStudentPath;
        boolean clientPath = requestClientPath || proposalClientPath || businessClientPath || paymentClientPath;
        boolean protectedPath = isProtectedPath(path, method);
        boolean adminPath = path.startsWith("/api/admin/")
                || catalogAdminMutation;

        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    headers.remove(INTERNAL_TOKEN_HEADER);
                }))
                .build();

        // Les routes inter-services sont joignables uniquement sur les ports internes.
        if (path.contains("/internal/") || path.endsWith("/internal")) {
            return reject(sanitizedExchange, HttpStatus.FORBIDDEN);
        }

        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(sanitizedExchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return protectedPath
                    ? reject(sanitizedExchange, HttpStatus.UNAUTHORIZED)
                    : chain.filter(sanitizedExchange);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();

            if (!"access".equals(claims.get("tokenType", String.class))) {
                return reject(sanitizedExchange, HttpStatus.UNAUTHORIZED);
            }

            String role = claims.get("role", String.class);
            if (adminPath && !"ADMIN".equals(role)) {
                return reject(sanitizedExchange, HttpStatus.FORBIDDEN);
            }
            if (studentPath && !"STUDENT".equals(role)) {
                return reject(sanitizedExchange, HttpStatus.FORBIDDEN);
            }
            if (clientPath && !"CLIENT".equals(role)) {
                return reject(sanitizedExchange, HttpStatus.FORBIDDEN);
            }
            if (businessStaffPath && !"ADMIN".equals(role) && !"MODERATOR".equals(role)) {
                return reject(sanitizedExchange, HttpStatus.FORBIDDEN);
            }
            if (businessModeratorPath && !"MODERATOR".equals(role)) {
                return reject(sanitizedExchange, HttpStatus.FORBIDDEN);
            }

            String userId = claims.get("userId", String.class);
            if (userId == null || userId.isBlank()) {
                return reject(sanitizedExchange, HttpStatus.UNAUTHORIZED);
            }

            return userAccessVerifier.isEnabled(userId)
                    .flatMap(enabled -> {
                        if (!enabled) {
                            return reject(sanitizedExchange, HttpStatus.FORBIDDEN);
                        }
                        ServerWebExchange authenticatedExchange = sanitizedExchange.mutate()
                                .request(request -> request.headers(headers -> {
                                    headers.set(USER_ID_HEADER, userId);
                                    headers.set(USER_EMAIL_HEADER, claims.getSubject());
                                    headers.set(USER_ROLE_HEADER, role);
                                }))
                                .build();
                        return chain.filter(authenticatedExchange);
                    })
                    // Le controle de bannissement est fail-closed : une panne Identity
                    // ne doit jamais redonner acces a un compte potentiellement suspendu.
                    .onErrorResume(exception -> reject(sanitizedExchange, HttpStatus.SERVICE_UNAVAILABLE));
        } catch (RuntimeException exception) {
            return reject(sanitizedExchange, HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isProtectedPath(String path, HttpMethod method) {
        return path.equals("/api/profiles/me")
                || path.startsWith("/api/verifications")
                || path.startsWith("/api/admin/")
                || path.equals("/api/storage/upload")
                || path.startsWith("/api/storage/private/")
                || path.equals("/api/gigs/mine")
                || (path.startsWith("/api/gigs") && method != HttpMethod.GET)
                || ((path.startsWith("/api/categories") || path.startsWith("/api/cities"))
                    && method != HttpMethod.GET)
                || path.startsWith("/api/v1/proposals")
                || path.equals("/api/v1/requests/mine")
                || (path.startsWith("/api/v1/requests") && method != HttpMethod.GET)
                || path.startsWith("/api/orders")
                || path.startsWith("/api/deliverables")
                || path.startsWith("/api/revisions")
                || path.startsWith("/api/disputes")
                || path.startsWith("/api/abuse-reports")
                || (path.startsWith("/api/reviews")
                    && !(method == HttpMethod.GET && path.startsWith("/api/reviews/student/")))
                || path.startsWith("/api/payments")
                || path.startsWith("/api/chat")
                || path.startsWith("/api/notifications");
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
