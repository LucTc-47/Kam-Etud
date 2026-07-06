package net.codejava.business_service.repository;

import net.codejava.business_service.entity.AbuseReport;
import net.codejava.business_service.enums.AbuseReportReason;
import net.codejava.business_service.enums.AbuseReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AbuseReportRepository extends JpaRepository<AbuseReport, UUID> {
    boolean existsByDispute_IdAndTargetUserIdAndReasonAndStatus(
            UUID disputeId, UUID targetUserId, AbuseReportReason reason, AbuseReportStatus status);

    List<AbuseReport> findAllByOrderByCreatedAtDesc();
}
