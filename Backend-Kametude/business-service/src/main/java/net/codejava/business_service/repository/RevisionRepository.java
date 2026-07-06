package net.codejava.business_service.repository;

import net.codejava.business_service.entity.Revision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RevisionRepository extends JpaRepository<Revision, UUID> {
    List<Revision> findByOrderId(UUID orderId);
}
