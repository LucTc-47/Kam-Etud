package cm.kametud.requestservice.repository;

import cm.kametud.requestservice.model.GigRequest;
import cm.kametud.requestservice.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GigRequestRepository extends JpaRepository<GigRequest, UUID> {
    List<GigRequest> findByStatus(RequestStatus status);
    List<GigRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);
    List<GigRequest> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<GigRequest> findByCategory(String category);
}
