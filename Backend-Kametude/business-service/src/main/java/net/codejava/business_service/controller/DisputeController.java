package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.CreateDisputeRequest;
import net.codejava.business_service.dto.DisputeResponse;
import net.codejava.business_service.dto.DisputeResponseRequest;
import net.codejava.business_service.dto.ResolveDisputeRequest;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.service.DisputeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/disputes") @RequiredArgsConstructor
public class DisputeController {
    private final DisputeService disputeService;

    @PostMapping
    public ResponseEntity<DisputeResponse> create(@RequestHeader("X-User-Id") UUID userId,
                                                   @RequestHeader("X-User-Role") String role,
                                                   @Valid @RequestBody CreateDisputeRequest request) {
        requireRole(role, "CLIENT");
        return ResponseEntity.status(HttpStatus.CREATED).body(disputeService.create(userId, request));
    }

    @PatchMapping("/order/{orderId}/response")
    public ResponseEntity<DisputeResponse> respond(@RequestHeader("X-User-Id") UUID userId,
                                                    @RequestHeader("X-User-Role") String role,
                                                    @PathVariable UUID orderId,
                                                    @Valid @RequestBody DisputeResponseRequest request) {
        requireRole(role, "STUDENT");
        return ResponseEntity.ok(disputeService.respond(userId, orderId, request));
    }

    @GetMapping
    public ResponseEntity<List<DisputeResponse>> getAll(@RequestHeader("X-User-Role") String role) {
        requireModerator(role);
        return ResponseEntity.ok(disputeService.getAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DisputeResponse> resolve(@RequestHeader("X-User-Id") UUID userId,
                                                    @RequestHeader("X-User-Role") String role,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody ResolveDisputeRequest request) {
        requireModerator(role);
        return ResponseEntity.ok(disputeService.resolve(userId, id, request));
    }

    private void requireRole(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) throw new BusinessException(HttpStatus.FORBIDDEN, "Role requis : " + expected);
    }
    private void requireModerator(String role) {
        if (!"MODERATOR".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role moderateur requis");
        }
    }
}
