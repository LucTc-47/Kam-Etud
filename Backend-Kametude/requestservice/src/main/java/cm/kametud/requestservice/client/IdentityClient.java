package cm.kametud.requestservice.client;

import java.util.UUID;

public interface IdentityClient {
    ProfileSummary getProfile(UUID userId);
    StudentStatusResponse getStudentStatus(UUID studentId);
}
