package com.example.kametud_catalog.controller;

import com.example.kametud_catalog.dto.GigCreateRequest;
import com.example.kametud_catalog.dto.GigResponse;
import com.example.kametud_catalog.dto.GigActiveRequest;
import com.example.kametud_catalog.dto.GigRatingUpdateRequest;
import com.example.kametud_catalog.dto.PublicationRequest;
import com.example.kametud_catalog.exception.CatalogAccessDeniedException;
import com.example.kametud_catalog.service.GigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gigs")
@RequiredArgsConstructor
public class GigController {

    private final GigService gigService;

    @Value("${internal.service-token:change-this-internal-token}")
    private String internalServiceToken;

    @PostMapping
    public ResponseEntity<GigResponse> createGig(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody GigCreateRequest request) {
        requireStudent(role);
        GigResponse response = gigService.createGig(userId, request);
        return ResponseEntity.created(URI.create("/api/gigs/" + response.getId()))
                .body(response);
    }

    @GetMapping
    public List<GigResponse> searchGigs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location
    ) {
        return gigService.searchGigs(query, category, location);
    }

    @GetMapping("/mine")
    public List<GigResponse> getMyGigs(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        requireStudent(role);
        return gigService.getMyGigs(userId);
    }

    @GetMapping("/{gigId}")
    public GigResponse getGig(@PathVariable UUID gigId) {
        return gigService.getGig(gigId);
    }

    @PutMapping("/{gigId}")
    public GigResponse updateGig(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID gigId,
            @Valid @RequestBody GigCreateRequest request) {
        requireStudent(role);
        return gigService.updateGig(gigId, userId, request);
    }

    @PatchMapping("/{gigId}/publish")
    public GigResponse publishGig(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID gigId,
            @Valid @RequestBody(required = false) PublicationRequest request) {
        requireStudent(role);
        return gigService.publishGig(gigId, userId, request == null || request.published());
    }

    @PatchMapping("/{gigId}/active")
    public GigResponse setActive(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID gigId,
            @Valid @RequestBody GigActiveRequest request) {
        requireStudent(role);
        return gigService.setActive(gigId, userId, request.active());
    }

    @DeleteMapping("/{gigId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGig(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID gigId) {
        requireStudent(role);
        gigService.deleteGig(gigId, userId);
    }

    @PatchMapping("/internal/students/{studentId}/deactivate")
    public GigDeactivationResponse deactivateStudentGigs(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable UUID studentId) {
        if (!internalServiceToken.equals(token)) {
            throw new CatalogAccessDeniedException("Appel inter-service refuse");
        }
        return new GigDeactivationResponse(gigService.deactivateAllForStudent(studentId));
    }

    public record GigDeactivationResponse(int deactivatedCount) {
    }

    @PatchMapping("/internal/{gigId}/rating")
    public GigResponse updateRating(
            @RequestHeader("X-Internal-Service-Token") String token,
            @PathVariable UUID gigId,
            @Valid @RequestBody GigRatingUpdateRequest request) {
        if (!internalServiceToken.equals(token)) {
            throw new CatalogAccessDeniedException("Appel inter-service refuse");
        }
        return gigService.updateRating(gigId, request.rating(), request.reviewCount());
    }

    private void requireStudent(String role) {
        if (!"STUDENT".equalsIgnoreCase(role)) {
            throw new CatalogAccessDeniedException("Role etudiant requis");
        }
    }
}
