package com.example.kametud_catalog.client;

import com.example.kametud_catalog.exception.IdentityServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestIdentityClient implements IdentityClient {

    private final RestClient restClient;
    private final String studentStatusPath;
    private final String studentProfilePath;

    public RestIdentityClient(RestClient.Builder restClientBuilder, IdentityServiceProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.studentStatusPath = properties.getStudentStatusPath();
        this.studentProfilePath = properties.getStudentProfilePath();
    }

    @Override
    public StudentStatusResponse getStudentStatus(UUID studentId) {
        try {
            StudentStatusResponse response = restClient.get()
                    .uri(studentStatusPath, studentId)
                    .retrieve()
                    .body(StudentStatusResponse.class);

            if (response == null) {
                throw new IdentityServiceUnavailableException("Identity Service returned an empty student status");
            }

            return response;
        } catch (RestClientException exception) {
            throw new IdentityServiceUnavailableException("Unable to verify student status with Identity Service", exception);
        }
    }

    @Override
    public StudentProfileSummary getStudentProfile(UUID studentId) {
        try {
            StudentProfileSummary response = restClient.get()
                    .uri(studentProfilePath, studentId)
                    .retrieve()
                    .body(StudentProfileSummary.class);
            if (response == null) {
                throw new IdentityServiceUnavailableException("Identity Service returned an empty profile");
            }
            return response;
        } catch (RestClientException exception) {
            throw new IdentityServiceUnavailableException("Unable to load student profile from Identity Service", exception);
        }
    }
}
