package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.client.CatalogClient;
import net.codejava.business_service.client.CatalogGig;
import net.codejava.business_service.client.CatalogTier;
import net.codejava.business_service.client.IdentityClient;
import net.codejava.business_service.client.SupportClient;
import net.codejava.business_service.dto.OrderRequest;
import net.codejava.business_service.dto.OrderResponse;
import net.codejava.business_service.dto.ProposalOrderRequest;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.OrderStatus;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdentityClient identityClient;
    private final CatalogClient catalogClient;
    private final SupportClient supportClient;

    @Transactional
    public OrderResponse createOrder(UUID authenticatedClientId, OrderRequest request) {
        CatalogGig gig = catalogClient.getGig(request.getGigId());
        if (!gig.active() || !gig.published()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ce service n'est pas disponible");
        }
        if (authenticatedClientId.equals(gig.studentId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Vous ne pouvez pas commander votre propre service");
        }
        CatalogTier selectedTier = gig.tier(request.getTier());
        if (selectedTier == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Palier inconnu");
        }

        Order order = Order.builder()
                // Ancien code : clientId, noms, titre et budget provenaient du corps JSON.
                .clientId(authenticatedClientId)
                .clientName(identityClient.getProfile(authenticatedClientId).displayName())
                .studentId(gig.studentId())
                .studentName(gig.studentName() == null ? "Etudiant" : gig.studentName())
                .gigId(gig.id())
                .gigTitle(gig.title())
                .tier(request.getTier().toLowerCase())
                .description(request.getDescription().trim())
                .budget(selectedTier.price().doubleValue())
                .escrowAmount(selectedTier.price().doubleValue())
                .paymentMethod(request.getPaymentMethod())
                .deliveryDate(LocalDateTime.now().plusDays(selectedTier.deliveryDays()))
                .revisionsLeft(2)
                .status(OrderStatus.PENDING)
                .build();
        Order saved = orderRepository.save(order);
        supportClient.notify(saved.getStudentId(), "Nouvelle mission", saved.getGigTitle(),
                "ORDER_UPDATE", "/mes-missions");
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse createFromProposal(ProposalOrderRequest request) {
        return orderRepository.findBySourceProposalId(request.getSourceProposalId())
                .map(this::toResponse)
                .orElseGet(() -> {
                    Order order = Order.builder()
                            .clientId(request.getClientId())
                            .clientName(request.getClientName())
                            .studentId(request.getStudentId())
                            .studentName(request.getStudentName())
                            .sourceRequestId(request.getSourceRequestId())
                            .sourceProposalId(request.getSourceProposalId())
                            .gigTitle(request.getTitle())
                            .tier("standard")
                            .description(request.getDescription())
                            .budget(request.getBudget())
                            .escrowAmount(request.getBudget())
                            .paymentMethod("mobile_money")
                            .deliveryDate(LocalDateTime.now().plusDays(request.getDeliveryDays()))
                            .revisionsLeft(2)
                            .status(OrderStatus.PENDING)
                            .build();
                    Order saved = orderRepository.save(order);
                    supportClient.notify(saved.getStudentId(), "Proposition acceptee",
                            "Une nouvelle mission vous attend", "PROPOSAL", "/mes-missions");
                    return toResponse(saved);
                });
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id, UUID userId, String role) {
        Order order = findOrThrow(id);
        ensureParticipantOrAdmin(order, userId, role);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getInternalOrder(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public OrderResponse markPaymentHeld(UUID id) {
        Order order = findOrThrow(id);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.ACCEPTED);
            order.setAcceptedAt(LocalDateTime.now());
            Order saved = orderRepository.save(order);
            supportClient.notify(saved.getStudentId(), "Paiement confirme",
                    saved.getGigTitle() + " peut maintenant commencer", "PAYMENT_CONFIRMED", "/mes-missions");
            return toResponse(saved);
        }
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REFUNDED
                || order.getStatus() == OrderStatus.REJECTED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Cette commande ne peut plus recevoir de paiement");
        }
        // Idempotence : une synchronisation tardive ne fait pas reculer le workflow.
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByClient(UUID clientId) {
        return orderRepository.findByClientIdOrderByCreatedAtDesc(clientId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByStudent(UUID studentId) {
        return orderRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(this::toResponse).toList();
    }

    Order findOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Commande introuvable"));
    }

    void ensureParticipantOrAdmin(Order order, UUID userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role)) return;
        if (!order.getClientId().equals(userId) && !order.getStudentId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Cette commande ne vous appartient pas");
        }
    }

    OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .clientId(order.getClientId())
                .clientName(order.getClientName())
                .studentId(order.getStudentId())
                .studentName(order.getStudentName())
                .gigId(order.getGigId())
                .sourceRequestId(order.getSourceRequestId())
                .sourceProposalId(order.getSourceProposalId())
                .gigTitle(order.getGigTitle())
                .tier(order.getTier())
                .description(order.getDescription())
                .budget(order.getBudget())
                .status(order.getStatus().name().toLowerCase())
                .revisionsLeft(order.getRevisionsLeft())
                .deliveryDate(order.getDeliveryDate())
                .escrowAmount(order.getEscrowAmount())
                .paymentMethod(order.getPaymentMethod())
                .deliverableUrl(order.getDeliverableUrl())
                .deliverableNote(order.getDeliverableNote())
                .deliveredAt(order.getDeliveredAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
