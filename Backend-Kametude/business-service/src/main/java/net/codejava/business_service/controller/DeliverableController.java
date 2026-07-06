package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.DeliverableRequest;
import net.codejava.business_service.dto.DeliverableResponse;
import net.codejava.business_service.service.DeliverableService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliverables")
@RequiredArgsConstructor
public class DeliverableController {

    private final DeliverableService deliverableService;

    @PostMapping
    public ResponseEntity<DeliverableResponse> submit(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody DeliverableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliverableService.submit(userId, role, request));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<DeliverableResponse>> getByOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(deliverableService.getByOrder(orderId, userId, role));
    }
}
