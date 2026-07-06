package com.example.kametud_catalog.controller;

import com.example.kametud_catalog.dto.ActiveRequest;
import com.example.kametud_catalog.dto.CatalogOptionResponse;
import com.example.kametud_catalog.dto.NameRequest;
import com.example.kametud_catalog.exception.CatalogAccessDeniedException;
import com.example.kametud_catalog.service.ReferenceDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReferenceDataController {
    private final ReferenceDataService referenceDataService;

    @GetMapping("/api/categories")
    public List<CatalogOptionResponse> categories() {
        return referenceDataService.categories();
    }

    @PostMapping("/api/categories")
    public ResponseEntity<CatalogOptionResponse> createCategory(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody NameRequest request) {
        requireAdmin(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(referenceDataService.createCategory(request.name()));
    }

    @PatchMapping("/api/categories/{id}")
    public CatalogOptionResponse updateCategory(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @Valid @RequestBody ActiveRequest request) {
        requireAdmin(role);
        return referenceDataService.updateCategory(id, request.active());
    }

    @DeleteMapping("/api/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@RequestHeader("X-User-Role") String role, @PathVariable UUID id) {
        requireAdmin(role);
        referenceDataService.deleteCategory(id);
    }

    @GetMapping("/api/cities")
    public List<CatalogOptionResponse> cities(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return referenceDataService.cities(includeInactive);
    }

    @PostMapping("/api/cities")
    public ResponseEntity<CatalogOptionResponse> createCity(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody NameRequest request) {
        requireAdmin(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(referenceDataService.createCity(request.name()));
    }

    @DeleteMapping("/api/cities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCity(@RequestHeader("X-User-Role") String role, @PathVariable UUID id) {
        requireAdmin(role);
        referenceDataService.deleteCity(id);
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new CatalogAccessDeniedException("Role admin requis");
        }
    }
}
