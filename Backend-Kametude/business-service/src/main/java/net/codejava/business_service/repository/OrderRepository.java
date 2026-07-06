package net.codejava.business_service.repository;

import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<Order> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    Optional<Order> findBySourceProposalId(UUID sourceProposalId);

    List<Order> findByStudentIdAndStatus(UUID studentId, OrderStatus status);

    long countByStudentIdAndStatus(UUID studentId, OrderStatus status);

    List<Order> findByStatusAndDeliveredAtBefore(OrderStatus status, java.time.LocalDateTime cutoff);

    List<Order> findByStatusAndAutoValidatedAtIsNotNullAndPayoutReleasedAtIsNull(OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(UUID id);
}
