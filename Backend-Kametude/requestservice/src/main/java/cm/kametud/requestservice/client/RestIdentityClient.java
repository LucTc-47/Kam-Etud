package cm.kametud.requestservice.client;

import cm.kametud.requestservice.exception.IdentityServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class RestIdentityClient implements IdentityClient {
    private final RestClient restClient;

    public RestIdentityClient(
            RestClient.Builder builder,
            @Value("${identity.service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public ProfileSummary getProfile(UUID userId) {
        try {
            ProfileSummary response = restClient.get()
                    .uri("/api/profiles/{userId}", userId)
                    .retrieve()
                    .body(ProfileSummary.class);
            if (response == null) throw new IdentityServiceUnavailableException("Profil Identity vide");
            return response;
        } catch (RestClientException exception) {
            throw new IdentityServiceUnavailableException("Identity Service indisponible", exception);
        }
    }

    @Override
    public StudentStatusResponse getStudentStatus(UUID studentId) {
        try {
            StudentStatusResponse response = restClient.get()
                    .uri("/api/students/{studentId}/status", studentId)
                    .retrieve()
                    .body(StudentStatusResponse.class);
            if (response == null) throw new IdentityServiceUnavailableException("Statut Identity vide");
            return response;
        } catch (RestClientException exception) {
            throw new IdentityServiceUnavailableException("Identity Service indisponible", exception);
        }
    }
}
