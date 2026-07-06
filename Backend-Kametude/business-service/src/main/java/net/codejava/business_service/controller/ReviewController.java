package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.CreateReviewRequest;
import net.codejava.business_service.dto.ReviewResponse;
import net.codejava.business_service.dto.ReportReviewRequest;
import net.codejava.business_service.dto.ModerateReviewRequest;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/reviews") @RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<ReviewResponse>> getByStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(reviewService.getByStudent(studentId));
    }

    @GetMapping("/reported")
    public ResponseEntity<List<ReviewResponse>> getReported(@RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !"MODERATOR".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role administrateur requis");
        }
        return ResponseEntity.ok(reviewService.getReported());
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@RequestHeader("X-User-Id") UUID userId,
                                                  @RequestHeader("X-User-Role") String role,
                                                  @Valid @RequestBody CreateReviewRequest request) {
        if (!"CLIENT".equalsIgnoreCase(role)) throw new BusinessException(HttpStatus.FORBIDDEN, "Role client requis");
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(userId, request));
    }

    @PatchMapping("/{reviewId}/report")
    public ResponseEntity<ReviewResponse> report(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReportReviewRequest request) {
        if (!"STUDENT".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role etudiant requis");
        }
        return ResponseEntity.ok(reviewService.report(reviewId, userId, request.reason()));
    }

    @PatchMapping("/{reviewId}/moderate")
    public ResponseEntity<ReviewResponse> moderate(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ModerateReviewRequest request) {
        if (!"ADMIN".equalsIgnoreCase(role) && !"MODERATOR".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role moderateur requis");
        }
        return ResponseEntity.ok(reviewService.moderate(reviewId, userId, request.action(), request.note()));
    }
}
