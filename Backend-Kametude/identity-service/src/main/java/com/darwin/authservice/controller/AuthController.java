package com.darwin.authservice.controller;

import com.darwin.authservice.dto.AuthResponse;
import com.darwin.authservice.dto.LoginRequest;
import com.darwin.authservice.dto.RefreshTokenRequest;
import com.darwin.authservice.dto.RegisterRequest;
import com.darwin.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        /* Ancien contrat : @RequestParam String refreshToken.
           Le token n'est plus place dans l'URL afin d'eviter les logs de query string. */
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validate(
            @RequestParam String token) {
        try {
            return ResponseEntity.ok(authService.validateToken(token));
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }
}
