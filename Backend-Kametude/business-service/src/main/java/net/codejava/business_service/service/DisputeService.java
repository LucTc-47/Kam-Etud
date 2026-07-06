package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.*;
import net.codejava.business_service.entity.Dispute;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.DisputeStatus;
import net.codejava.business_service.enums.OrderStatus;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.repository.DisputeRepository;
import net.codejava.business_service.repository.OrderRepository;
import net.codejava.business_service.client.SupportClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class DisputeService {
    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final SupportClient supportClient;

    @Transactional
    public DisputeResponse create(UUID clientId, CreateDisputeRequest request) {
        Order order = orderService.findOrThrow(request.getOrderId());
        if (!order.getClientId().equals(clientId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Commande d'un autre client");
        }
        if (disputeRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Un litige existe deja pour cette commande");
        }
        if (!(order.getStatus() == OrderStatus.IN_PROGRESS || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.REVISION_REQUESTED)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Cette commande ne peut pas etre contestee");
        }
        order.setStatus(OrderStatus.DISPUTED);
        orderRepository.save(order);
        Dispute dispute = Dispute.builder().orderId(order.getId()).gigTitle(order.getGigTitle())
                .clientId(order.getClientId()).clientName(order.getClientName())
                .clientStatement(request.getClientStatement()).clientEvidenceUrl(request.getClientEvidenceUrl())
                .studentId(order.getStudentId()).studentName(order.getStudentName())
                .status(DisputeStatus.OPEN).build();
        Dispute saved = disputeRepository.save(dispute);
        supportClient.notify(saved.getStudentId(), "Litige ouvert", saved.getGigTitle(),
                "ORDER_UPDATE", "/mes-missions");
        return toResponse(saved);
    }

    @Transactional
    public DisputeResponse respond(UUID studentId, UUID orderId, DisputeResponseRequest request) {
        Dispute dispute = disputeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Litige introuvable"));
        if (!dispute.getStudentId().equals(studentId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Litige d'un autre etudiant");
        }
        dispute.setStudentStatement(request.getStatement());
        dispute.setStudentEvidenceUrl(request.getEvidenceUrl());
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        Dispute saved = disputeRepository.save(dispute);
        supportClient.notify(saved.getClientId(), "Reponse au litige", saved.getGigTitle(),
                "ORDER_UPDATE", "/mes-commandes");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DisputeResponse> getAll() {
        return disputeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public DisputeResponse resolve(UUID moderatorId, UUID id, ResolveDisputeRequest request) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Litige introuvable"));
        DisputeStatus status;
        try {
            status = DisputeStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Resolution inconnue");
        }
        if (status != DisputeStatus.RESOLVED_CLIENT && status != DisputeStatus.RESOLVED_STUDENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Resolution finale requise");
        }
        Order order = orderService.findOrThrow(dispute.getOrderId());
        order.setStatus(status == DisputeStatus.RESOLVED_CLIENT ? OrderStatus.REFUNDED : OrderStatus.COMPLETED);
        orderRepository.save(order);
        dispute.setStatus(status);
        dispute.setModeratorId(moderatorId);
        dispute.setModeratorNote(request.getModeratorNote());
        dispute.setResolvedAt(LocalDateTime.now());
        Dispute saved = disputeRepository.save(dispute);
        supportClient.notify(saved.getClientId(), "Litige resolu", saved.getStatus().name().toLowerCase(),
                "ORDER_UPDATE", "/mes-commandes");
        supportClient.notify(saved.getStudentId(), "Litige resolu", saved.getStatus().name().toLowerCase(),
                "ORDER_UPDATE", "/mes-missions");
        return toResponse(saved);
    }

    private DisputeResponse toResponse(Dispute d) {
        return DisputeResponse.builder().id(d.getId()).orderId(d.getOrderId()).gigTitle(d.getGigTitle())
                .clientId(d.getClientId()).clientName(d.getClientName()).clientStatement(d.getClientStatement())
                .clientEvidenceUrl(d.getClientEvidenceUrl()).studentId(d.getStudentId())
                .studentName(d.getStudentName()).studentStatement(d.getStudentStatement())
                .studentEvidenceUrl(d.getStudentEvidenceUrl()).status(d.getStatus().name().toLowerCase())
                .moderatorId(d.getModeratorId()).moderatorNote(d.getModeratorNote())
                .createdAt(d.getCreatedAt()).resolvedAt(d.getResolvedAt()).build();
    }
}
