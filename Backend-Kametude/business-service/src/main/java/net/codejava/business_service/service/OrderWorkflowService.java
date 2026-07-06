package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.OrderResponse;
import net.codejava.business_service.dto.OrderUpdateRequest;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.OrderStatus;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.repository.OrderRepository;
import net.codejava.business_service.client.SupportClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderWorkflowService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final SupportClient supportClient;

    @Transactional
    public OrderResponse update(UUID id, UUID userId, String role, OrderUpdateRequest request) {
        Order order = orderService.findOrThrow(id);
        OrderStatus target = parseStatus(request.getStatus());
        authorizeTransition(order, userId, role, target);
        validateTransition(order.getStatus(), target);

        if (target == OrderStatus.REVISION_REQUESTED) {
            if (order.getRevisionsLeft() == null || order.getRevisionsLeft() <= 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "Aucune revision restante");
            }
            order.setRevisionsLeft(order.getRevisionsLeft() - 1);
        }
        if (target == OrderStatus.DELIVERED) {
            if (request.getDeliverableUrl() == null || request.getDeliverableUrl().isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Le fichier livre est obligatoire");
            }
            order.setDeliverableUrl(request.getDeliverableUrl());
            order.setDeliverableNote(request.getDeliverableNote());
            order.setDeliveredAt(LocalDateTime.now());
        }
        order.setStatus(target);
        Order saved = orderRepository.save(order);
        UUID recipient = "CLIENT".equalsIgnoreCase(role) ? saved.getStudentId() : saved.getClientId();
        String link = recipient.equals(saved.getStudentId()) ? "/mes-missions" : "/mes-commandes";
        supportClient.notify(recipient, "Commande mise a jour",
                saved.getGigTitle() + " : " + target.name().toLowerCase(), "ORDER_UPDATE", link);
        return orderService.toResponse(saved);
    }

    private void authorizeTransition(Order order, UUID userId, String role, OrderStatus target) {
        boolean client = "CLIENT".equalsIgnoreCase(role) && order.getClientId().equals(userId);
        boolean student = "STUDENT".equalsIgnoreCase(role) && order.getStudentId().equals(userId);
        boolean admin = "ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role);
        Set<OrderStatus> clientTargets = Set.of(OrderStatus.COMPLETED, OrderStatus.REVISION_REQUESTED,
                OrderStatus.DISPUTED, OrderStatus.CANCELLED);
        Set<OrderStatus> studentTargets = Set.of(OrderStatus.IN_PROGRESS, OrderStatus.DELIVERED,
                OrderStatus.CANCELLED);
        if (!(admin || (client && clientTargets.contains(target)) || (student && studentTargets.contains(target)))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Transition non autorisee pour cet utilisateur");
        }
    }

    private void validateTransition(OrderStatus current, OrderStatus target) {
        boolean valid = switch (target) {
            // Le travail ne commence qu'apres confirmation du sequestre par Payment Service.
            case IN_PROGRESS -> current == OrderStatus.ACCEPTED;
            case DELIVERED -> current == OrderStatus.IN_PROGRESS || current == OrderStatus.REVISION_REQUESTED;
            case COMPLETED -> current == OrderStatus.DELIVERED;
            case REVISION_REQUESTED -> current == OrderStatus.DELIVERED;
            case DISPUTED -> current == OrderStatus.IN_PROGRESS || current == OrderStatus.DELIVERED
                    || current == OrderStatus.REVISION_REQUESTED;
            case CANCELLED -> current == OrderStatus.PENDING;
            case REFUNDED -> current == OrderStatus.DISPUTED;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Transition invalide de " + current + " vers " + target);
        }
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Statut de commande inconnu");
        }
    }

    /* Ancien workflow : accept/reject/start/deliver sans identite utilisateur.
       Les routes historiques appellent maintenant update() avec les controles JWT. */
}
