package com.kametud.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void registersEveryMicroserviceRoute() {
        List<String> routeIds = routeDefinitionLocator.getRouteDefinitions()
                .map(route -> route.getId())
                .collectList()
                .block();

        assertThat(routeIds).containsExactlyInAnyOrder(
                "identity-auth",
                "identity-profiles",
                "catalog-service",
                "request-service",
                "business-service",
                "payment-service",
                "support-service-http",
                "support-service-websocket"
        );
    }
}
