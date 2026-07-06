package com.example.kametud_catalog.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "identity.service")
public class IdentityServiceProperties {

    private String baseUrl = "http://localhost:8081";
    private String studentStatusPath = "/api/students/{studentId}/status";
    private String studentProfilePath = "/api/profiles/{studentId}";
}
