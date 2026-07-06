package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.StudentStatisticsResponse;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.enums.OrderStatus;
import net.codejava.business_service.repository.OrderRepository;
import net.codejava.business_service.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentStatisticsService {
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public StudentStatisticsResponse get(UUID studentId) {
        List<Order> completedOrders = orderRepository.findByStudentIdAndStatus(studentId, OrderStatus.COMPLETED);
        long completedJobs = completedOrders.size();
        long reviewCount = reviewRepository.countByStudentIdAndHiddenFalse(studentId);
        Double average = reviewRepository.averageVisibleRating(studentId);
        double rating = average == null ? 0.0 : Math.round(average * 100.0) / 100.0;

        long xp = completedJobs * 100L + reviewCount * 20L;
        String badge;
        long nextLevel;
        if (xp >= 3000) {
            badge = "Expert";
            nextLevel = Math.max(xp, 3000);
        } else if (xp >= 1500) {
            badge = "Avanc\u00e9";
            nextLevel = 3000;
        } else if (xp >= 500) {
            badge = "Interm\u00e9diaire";
            nextLevel = 1500;
        } else {
            badge = "D\u00e9butant";
            nextLevel = 500;
        }

        return new StudentStatisticsResponse(completedJobs, reviewCount, rating,
                averageAcceptanceTime(completedOrders), badge, xp, nextLevel);
    }

    private String averageAcceptanceTime(List<Order> orders) {
        var durations = orders.stream()
                .filter(order -> order.getCreatedAt() != null && order.getAcceptedAt() != null)
                .map(order -> Duration.between(order.getCreatedAt(), order.getAcceptedAt()))
                .toList();
        if (durations.isEmpty()) return "Non mesur\u00e9";
        double hours = durations.stream().mapToLong(Duration::toMinutes).average().orElse(0) / 60.0;
        if (hours < 1) return "< 1h";
        return String.format(Locale.ROOT, "%.1fh", hours);
    }
}
