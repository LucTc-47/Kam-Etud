package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.AbuseReportResponse;
import net.codejava.business_service.dto.CreateAbuseReportRequest;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.service.AbuseReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/abuse-reports")
@RequiredArgsConstructor
public class AbuseReportController {
    private final AbuseReportService abuseReportService;

    @PostMapping
    public ResponseEntity<AbuseReportResponse> create(
            @RequestHeader("X-User-Id") UUID moderatorId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateAbuseReportRequest request) {
        if (!"MODERATOR".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role moderateur requis");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(abuseReportService.create(moderatorId, request));
    }
}
