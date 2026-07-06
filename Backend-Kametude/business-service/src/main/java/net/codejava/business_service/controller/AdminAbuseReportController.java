package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.AbuseReportResponse;
import net.codejava.business_service.dto.DecideAbuseReportRequest;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.service.AbuseReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/abuse-reports")
@RequiredArgsConstructor
public class AdminAbuseReportController {
    private final AbuseReportService abuseReportService;

    @GetMapping
    public ResponseEntity<List<AbuseReportResponse>> getAll(@RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(abuseReportService.getAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AbuseReportResponse> decide(
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @Valid @RequestBody DecideAbuseReportRequest request) {
        requireAdmin(role);
        return ResponseEntity.ok(abuseReportService.decide(adminId, id, request));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role administrateur requis");
        }
    }
}
