package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.OrderResponse;
import net.codejava.business_service.dto.OrderUpdateRequest;
import net.codejava.business_service.service.OrderWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderWorkflowController {

    private final OrderWorkflowService workflowService;

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> update(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(workflowService.update(id, userId, role, request));
    }

    /* Anciennes routes /accept, /reject, /start, /deliver, /complete,
       /request-revision et /dispute sans controle utilisateur remplacees
       par PATCH /{id}/status avec verification du participant. */
}
