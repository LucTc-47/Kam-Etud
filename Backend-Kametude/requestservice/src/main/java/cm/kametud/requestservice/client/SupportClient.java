package cm.kametud.requestservice.client;

import java.util.UUID;

public interface SupportClient {
    void notify(UUID userId, String title, String message, String type, String link);
}
