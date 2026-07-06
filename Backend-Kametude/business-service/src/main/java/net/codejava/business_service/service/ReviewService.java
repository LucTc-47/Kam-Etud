package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.client.IdentityClient;
import net.codejava.business_service.client.CatalogClient;
import net.codejava.business_service.dto.CreateReviewRequest;
import net.codejava.business_service.dto.ReviewResponse;
import net.codejava.business_service.entity.Order;
import net.codejava.business_service.entity.Review;
import net.codejava.business_service.enums.OrderStatus;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.repository.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final OrderService orderService;
    private final IdentityClient identityClient;
    private final CatalogClient catalogClient;

    @Transactional(readOnly = true)
    public List<ReviewResponse> getByStudent(UUID studentId) {
        return reviewRepository.findByStudentIdAndHiddenFalseOrderByCreatedAtDesc(studentId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReported() {
        return reviewRepository.findByReportedTrueOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReviewResponse create(UUID clientId, CreateReviewRequest request) {
        Order order = orderService.findOrThrow(request.getOrderId());
        if (!order.getClientId().equals(clientId)) throw new BusinessException(HttpStatus.FORBIDDEN, "Commande d'un autre client");
        if (order.getStatus() != OrderStatus.COMPLETED) throw new BusinessException(HttpStatus.CONFLICT, "Commande non terminee");
        if (reviewRepository.existsByOrderIdAndReviewerId(order.getId(), clientId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Avis deja publie");
        }
        Review review = Review.builder().orderId(order.getId()).gigId(order.getGigId())
                .reviewerId(clientId).reviewerName(identityClient.getProfile(clientId).displayName())
                .studentId(order.getStudentId()).rating(request.getRating()).text(request.getText())
                .reported(false).build();
        Review saved = reviewRepository.saveAndFlush(review);
        synchronizeGigRating(saved.getGigId());
        return toResponse(saved);
    }

    @Transactional
    public ReviewResponse report(UUID reviewId, UUID studentId, String reason) {
        Review review = findOrThrow(reviewId);
        if (!review.getStudentId().equals(studentId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Seul l'etudiant concerne peut signaler cet avis");
        }
        if (review.isHidden()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Cet avis est deja masque");
        }
        review.setReported(true);
        review.setReportReason(reason.trim());
        review.setReportedAt(LocalDateTime.now());
        return toResponse(reviewRepository.save(review));
    }

    public void synchronizeGigRating(UUID gigId) {
        if (gigId == null) return;
        long reviewCount = reviewRepository.countByGigIdAndHiddenFalse(gigId);
        Double average = reviewRepository.averageVisibleRatingByGigId(gigId);
        double rating = average == null ? 0.0 : Math.round(average * 100.0) / 100.0;
        catalogClient.updateRating(gigId, rating, reviewCount);
    }

    @Transactional
    public ReviewResponse moderate(UUID reviewId, UUID moderatorId, String action, String note) {
        Review review = findOrThrow(reviewId);
        String normalizedAction = action == null ? "" : action.trim().toUpperCase();
        switch (normalizedAction) {
            case "HIDE" -> review.setHidden(true);
            case "DISMISS" -> review.setHidden(false);
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "Action de moderation inconnue");
        }
        review.setReported(false);
        review.setModeratedBy(moderatorId);
        review.setModeratedAt(LocalDateTime.now());
        review.setModerationNote(note == null ? null : note.trim());
        Review saved = reviewRepository.saveAndFlush(review);
        synchronizeGigRating(saved.getGigId());
        return toResponse(saved);
    }

    private Review findOrThrow(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Avis introuvable"));
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder().id(r.getId()).orderId(r.getOrderId()).gigId(r.getGigId())
                .reviewerId(r.getReviewerId()).reviewerName(r.getReviewerName()).studentId(r.getStudentId())
                .rating(r.getRating()).text(r.getText()).reported(r.isReported()).createdAt(r.getCreatedAt()).build();
    }
}
