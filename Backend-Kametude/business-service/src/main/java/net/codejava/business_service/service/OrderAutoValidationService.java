package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.codejava.business_service.client.PaymentClient;
import net.codejava.business_service.dto.AutoValidationResponse;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.OrderStatus;
import net.codejava.business_service.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutoValidationService {
    private final OrderRepository orderRepository;
    private final OrderAutoValidationWorker worker;
    private final PaymentClient paymentClient;

    @Value("${orders.auto-validation-delay:PT24H}")
    private Duration validationDelay;

    @Scheduled(initialDelayString = "${orders.auto-validation-initial-delay-ms:15000}",
            fixedDelayString = "${orders.auto-validation-scan-ms:60000}")
    public void scheduledRun() {
        AutoValidationResponse result = runNow();
        if (result.eligible() > 0 || result.pending() > 0) {
            log.info("Auto-validation: {} eligibles, {} validees, {} versements, {} en attente",
                    result.eligible(), result.validated(), result.released(), result.pending());
        }
    }

    public AutoValidationResponse runNow() {
        LocalDateTime cutoff = LocalDateTime.now().minus(validationDelay);
        var dueOrders = orderRepository.findByStatusAndDeliveredAtBefore(OrderStatus.DELIVERED, cutoff);
        int validated = 0;
        for (Order order : dueOrders) {
            if (worker.markCompletedIfDue(order.getId(), cutoff)) validated++;
        }

        Set<UUID> pendingPayouts = new LinkedHashSet<>();
        orderRepository.findByStatusAndAutoValidatedAtIsNotNullAndPayoutReleasedAtIsNull(OrderStatus.COMPLETED)
                .forEach(order -> pendingPayouts.add(order.getId()));

        int released = 0;
        for (UUID orderId : pendingPayouts) {
            try {
                paymentClient.releaseAutomatically(orderId);
                worker.markPayoutReleased(orderId);
                released++;
            } catch (RuntimeException exception) {
                // L'ordre reste marque en attente et sera repris au prochain passage.
                log.warn("Versement automatique differe pour la commande {}", orderId);
            }
        }
        return new AutoValidationResponse(dueOrders.size(), validated, released,
                Math.max(0, pendingPayouts.size() - released));
    }
}
