package net.codejava.business_service.repository;

import net.codejava.business_service.entity.Dispute;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    Optional<Dispute> findByOrderId(UUID orderId);
    boolean existsByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") UUID id);
}
