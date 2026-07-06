package com.kametude.payment.controller;

import com.kametude.payment.dto.PaymentRequestDTO;
import com.kametude.payment.dto.PaymentResponseDTO;
import com.kametude.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
// Ancien import inutilise : les statuts sont geres par ResponseEntity et le handler global.
// import org.springframework.http.HttpStatus;
import com.kametude.payment.exception.PaymentException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @Value("${internal.service-token:change-this-internal-token}")
    private String internalServiceToken;

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponseDTO> initiate(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody PaymentRequestDTO request) {
        return ResponseEntity.ok(paymentService.initiate(userId, role, request));
    }

    @PostMapping("/order/{orderId}/verify")
    public ResponseEntity<PaymentResponseDTO> verify(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.verify(orderId, userId, role));
    }

    @PostMapping("/order/{orderId}/release")
    public ResponseEntity<PaymentResponseDTO> release(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.releaseByOrder(orderId, userId, role));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponseDTO> getByOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getByOrder(orderId, userId, role));
    }

    @PostMapping("/internal/order/{orderId}/release")
    public ResponseEntity<PaymentResponseDTO> releaseAutomatically(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable UUID orderId) {
        if (!internalServiceToken.equals(token)) {
            throw new PaymentException("PAYMENT_FORBIDDEN", "Appel inter-service refuse");
        }
        return ResponseEntity.ok(paymentService.releaseAutomatically(orderId));
    }

    /* Anciennes routes POST /api/payments et /{transactionId}/release commentees :
       elles acceptaient montant et destinataire depuis le navigateur. */
}
