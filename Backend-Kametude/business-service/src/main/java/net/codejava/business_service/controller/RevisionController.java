package net.codejava.business_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.RevisionRequest;
import net.codejava.business_service.dto.RevisionResponse;
import net.codejava.business_service.service.RevisionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/revisions")
@RequiredArgsConstructor
public class RevisionController {

    private final RevisionService revisionService;

    @PostMapping
    public ResponseEntity<RevisionResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody RevisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(revisionService.create(userId, role, request));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<RevisionResponse>> getByOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(revisionService.getByOrder(orderId, userId, role));
    }
}
