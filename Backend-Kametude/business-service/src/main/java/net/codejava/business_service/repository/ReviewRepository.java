package net.codejava.business_service.repository;

import net.codejava.business_service.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findByStudentIdAndHiddenFalseOrderByCreatedAtDesc(UUID studentId);
    List<Review> findByReportedTrueOrderByCreatedAtDesc();
    boolean existsByOrderIdAndReviewerId(UUID orderId, UUID reviewerId);

    long countByStudentIdAndHiddenFalse(UUID studentId);

    @Query("select avg(r.rating) from Review r where r.studentId = :studentId and r.hidden = false")
    Double averageVisibleRating(UUID studentId);

    long countByGigIdAndHiddenFalse(UUID gigId);

    @Query("select avg(r.rating) from Review r where r.gigId = :gigId and r.hidden = false")
    Double averageVisibleRatingByGigId(UUID gigId);
}
