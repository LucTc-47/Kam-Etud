package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.OrderRequest;
import net.codejava.business_service.dto.OrderResponse;
import net.codejava.business_service.dto.ProposalOrderRequest;
import net.codejava.business_service.exception.BusinessException;
import net.codejava.business_service.service.OrderService;
import net.codejava.business_service.service.OrderAutoValidationService;
import net.codejava.business_service.dto.AutoValidationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderAutoValidationService autoValidationService;

    @Value("${internal.service-token:change-this-internal-token}")
    private String internalServiceToken;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody OrderRequest request) {
        requireRole(role, "CLIENT");
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(userId, request));
    }

    @PostMapping("/internal/from-proposal")
    public ResponseEntity<OrderResponse> createFromProposal(
            @RequestHeader("X-Internal-Service-Token") String token,
            @Valid @RequestBody ProposalOrderRequest request) {
        if (!internalServiceToken.equals(token)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Appel inter-service refuse");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createFromProposal(request));
    }

    @GetMapping("/internal/{id}")
    public ResponseEntity<OrderResponse> getInternalOrder(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable UUID id) {
        if (!internalServiceToken.equals(token)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Appel inter-service refuse");
        }
        return ResponseEntity.ok(orderService.getInternalOrder(id));
    }

    @PostMapping("/internal/{id}/payment-held")
    public ResponseEntity<OrderResponse> markPaymentHeld(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable UUID id) {
        if (!internalServiceToken.equals(token)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Appel inter-service refuse");
        }
        return ResponseEntity.ok(orderService.markPaymentHeld(id));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<OrderResponse>> getMine(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        requireRole(role, "CLIENT");
        return ResponseEntity.ok(orderService.getOrdersByClient(userId));
    }

    @GetMapping("/missions")
    public ResponseEntity<List<OrderResponse>> getMissions(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        requireRole(role, "STUDENT");
        return ResponseEntity.ok(orderService.getOrdersByStudent(userId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @PostMapping("/auto-validation/run")
    public ResponseEntity<AutoValidationResponse> runAutoValidation(
            @RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(autoValidationService.runNow());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id, userId, role));
    }

    /* Anciennes routes GET /client/{clientId}, GET /student/{studentId} et GET /
       retirees : elles permettaient de choisir l'identite ou de tout lister. */

    private void requireRole(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role " + expected.toLowerCase() + " requis");
        }
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role) && !"MODERATOR".equalsIgnoreCase(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Role administrateur requis");
        }
    }
}
