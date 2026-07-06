package com.darwin.authservice.controller;

import com.darwin.authservice.dto.InternalAccessResponse;
import com.darwin.authservice.dto.InternalPayoutProfileResponse;
import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.entity.User;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalAccessController {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Value("${internal.service-token:change-this-internal-token}")
    private String internalServiceToken;

    @GetMapping("/{userId}/access")
    public InternalAccessResponse getAccess(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable UUID userId) {
        if (!internalServiceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appel inter-service refuse");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        return new InternalAccessResponse(userId, user.isEnabled());
    }

    @GetMapping("/{userId}/payout-profile")
    public InternalPayoutProfileResponse getPayoutProfile(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable UUID userId) {
        if (!internalServiceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appel inter-service refuse");
        }
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil introuvable"));
        return new InternalPayoutProfileResponse(profile.getPhone());
    }
}
