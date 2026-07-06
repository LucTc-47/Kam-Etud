package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.client.SupportClient;
import net.codejava.business_service.dto.AbuseReportResponse;
import net.codejava.business_service.dto.CreateAbuseReportRequest;
import net.codejava.business_service.dto.DecideAbuseReportRequest;
import net.codejava.business_service.entity.AbuseReport;
import net.codejava.business_service.entity.Dispute;
import net.codejava.business_service.enums.AbuseReportReason;
import net.codejava.business_service.enums.AbuseReportStatus;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.repository.AbuseReportRepository;
import net.codejava.business_service.repository.DisputeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AbuseReportService {
    private final AbuseReportRepository abuseReportRepository;
    private final DisputeRepository disputeRepository;
    private final SupportClient supportClient;

    @Transactional
    public AbuseReportResponse create(UUID moderatorId, CreateAbuseReportRequest request) {
        // Le verrou evite deux signalements ouverts identiques soumis en parallele.
        Dispute dispute = disputeRepository.findByIdForUpdate(request.getDisputeId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Litige introuvable"));
        AbuseReportReason reason = parseReason(request.getReason());

        String targetRole;
        String targetName;
        if (dispute.getClientId().equals(request.getTargetUserId())) {
            targetRole = "CLIENT";
            targetName = dispute.getClientName();
        } else if (dispute.getStudentId().equals(request.getTargetUserId())) {
            targetRole = "STUDENT";
            targetName = dispute.getStudentName();
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "La personne signalee ne participe pas a ce litige");
        }

        boolean duplicate = abuseReportRepository.existsByDispute_IdAndTargetUserIdAndReasonAndStatus(
                dispute.getId(), request.getTargetUserId(), reason, AbuseReportStatus.OPEN);
        if (duplicate) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Un signalement identique est deja ouvert");
        }

        AbuseReport report = AbuseReport.builder()
                .dispute(dispute)
                .targetUserId(request.getTargetUserId())
                .targetName(targetName)
                .targetRole(targetRole)
                .moderatorId(moderatorId)
                .reason(reason)
                .note(request.getNote().trim())
                .status(AbuseReportStatus.OPEN)
                .build();
        return toResponse(abuseReportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<AbuseReportResponse> getAll() {
        return abuseReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AbuseReportResponse decide(UUID adminId, UUID reportId, DecideAbuseReportRequest request) {
        AbuseReport report = abuseReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Signalement introuvable"));
        if (report.getStatus() != AbuseReportStatus.OPEN) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ce signalement a deja ete traite");
        }

        AbuseReportStatus status = switch (normalize(request.getAction())) {
            case "DISMISS" -> AbuseReportStatus.DISMISSED;
            case "WARN" -> AbuseReportStatus.WARNED;
            case "BAN" -> AbuseReportStatus.BANNED;
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "Decision inconnue");
        };

        report.setStatus(status);
        report.setAdminId(adminId);
        report.setAdminNote(request.getAdminNote().trim());
        report.setResolvedAt(LocalDateTime.now());
        AbuseReport saved = abuseReportRepository.save(report);

        if (status == AbuseReportStatus.WARNED) {
            String targetPath = "STUDENT".equals(saved.getTargetRole()) ? "/mes-missions" : "/mes-commandes";
            supportClient.notify(saved.getTargetUserId(), "Avertissement de moderation",
                    saved.getAdminNote(), "MODERATION", targetPath);
        }

        // BAN enregistre la decision disciplinaire. L'interface admin appelle d'abord
        // le mecanisme de bannissement Identity existant, sans toucher au paiement.
        return toResponse(saved);
    }

    private AbuseReportReason parseReason(String value) {
        try {
            return AbuseReportReason.valueOf(normalize(value));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Motif de signalement inconnu");
        }
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private AbuseReportResponse toResponse(AbuseReport report) {
        Dispute dispute = report.getDispute();
        return AbuseReportResponse.builder()
                .id(report.getId())
                .disputeId(dispute.getId())
                .orderId(dispute.getOrderId())
                .gigTitle(dispute.getGigTitle())
                .targetUserId(report.getTargetUserId())
                .targetName(report.getTargetName())
                .targetRole(report.getTargetRole().toLowerCase(Locale.ROOT))
                .moderatorId(report.getModeratorId())
                .reason(report.getReason().name().toLowerCase(Locale.ROOT))
                .note(report.getNote())
                .status(report.getStatus().name().toLowerCase(Locale.ROOT))
                .clientStatement(dispute.getClientStatement())
                .clientEvidenceUrl(dispute.getClientEvidenceUrl())
                .studentStatement(dispute.getStudentStatement())
                .studentEvidenceUrl(dispute.getStudentEvidenceUrl())
                .adminId(report.getAdminId())
                .adminNote(report.getAdminNote())
                .createdAt(report.getCreatedAt())
                .resolvedAt(report.getResolvedAt())
                .build();
    }
}
