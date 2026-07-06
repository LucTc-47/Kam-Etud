package com.darwin.authservice.controller;

import com.darwin.authservice.dto.AdminProfileUpdateRequest;
import com.darwin.authservice.dto.ProfileResponse;
import com.darwin.authservice.dto.ProfileUpdateRequest;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.getByUserId(userId));
    }

    @GetMapping("/profiles/me")
    public ResponseEntity<ProfileResponse> getCurrentProfile(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(profileService.getCurrent(user));
    }

    @PutMapping("/profiles/me")
    public ResponseEntity<ProfileResponse> updateCurrentProfile(
            @AuthenticationPrincipal User user,
            @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateCurrent(user, request));
    }

    @GetMapping("/admin/profiles")
    public ResponseEntity<List<ProfileResponse>> getAllProfiles() {
        return ResponseEntity.ok(profileService.getAll());
    }

    @PatchMapping("/admin/profiles/{userId}")
    public ResponseEntity<ProfileResponse> updateProfileByAdmin(
            @PathVariable UUID userId,
            @RequestBody AdminProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.updateByAdmin(userId, request));
    }

    /* Ancien contrat conserve pour montrer la migration :
       GET /profiles/{profileId} et PUT /profiles/{profileId}
       accedaient directement au repository et utilisaient l'id technique du profil.
       Le nouveau contrat utilise /api/profiles/{userId} et /api/profiles/me,
       afin que le frontend ne puisse modifier que le profil du JWT courant. */
}
