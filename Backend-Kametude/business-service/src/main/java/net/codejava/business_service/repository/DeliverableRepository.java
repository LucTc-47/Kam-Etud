package net.codejava.business_service.repository;

import net.codejava.business_service.entity.Deliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {
    List<Deliverable> findByOrderId(UUID orderId);
}
