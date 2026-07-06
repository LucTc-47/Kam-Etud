package com.darwin.authservice.controller;

import com.darwin.authservice.dto.CreateVerificationRequest;
import com.darwin.authservice.dto.StudentStatusResponse;
import com.darwin.authservice.dto.UpdateVerificationRequest;
import com.darwin.authservice.dto.VerificationResponse;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping("/verifications")
    public ResponseEntity<VerificationResponse> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateVerificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(verificationService.create(user, request));
    }

    @GetMapping("/verifications/me")
    public ResponseEntity<List<VerificationResponse>> getMine(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(verificationService.getMine(user));
    }

    @GetMapping("/admin/verifications")
    public ResponseEntity<List<VerificationResponse>> getAll() {
        return ResponseEntity.ok(verificationService.getAll());
    }

    @PatchMapping("/admin/verifications/{id}")
    public ResponseEntity<VerificationResponse> review(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVerificationRequest request) {
        return ResponseEntity.ok(verificationService.review(id, request));
    }

    @GetMapping("/students/{studentId}/status")
    public ResponseEntity<StudentStatusResponse> getStudentStatus(@PathVariable UUID studentId) {
        return ResponseEntity.ok(verificationService.getStudentStatus(studentId));
    }
}
