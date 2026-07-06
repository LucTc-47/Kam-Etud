package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.OrderStatus;
import net.codejava.business_service.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderAutoValidationWorker {
    private final OrderRepository orderRepository;

    @Transactional
    public boolean markCompletedIfDue(UUID orderId, LocalDateTime cutoff) {
        Order order = orderRepository.findLockedById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.DELIVERED
                || order.getDeliveredAt() == null || order.getDeliveredAt().isAfter(cutoff)) {
            return false;
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setAutoValidatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return true;
    }

    @Transactional
    public void markPayoutReleased(UUID orderId) {
        Order order = orderRepository.findLockedById(orderId).orElse(null);
        if (order != null && order.getAutoValidatedAt() != null) {
            order.setPayoutReleasedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
    }
}
