package com.kametud.gateway;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface UserAccessVerifier {
    Mono<Boolean> isEnabled(String userId);
}
