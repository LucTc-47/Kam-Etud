package cm.kametud.requestservice.controller;

import cm.kametud.requestservice.dto.CreateRequestDTO;
import cm.kametud.requestservice.dto.GigRequestDTO;
import cm.kametud.requestservice.exception.RequestDomainException;
import cm.kametud.requestservice.service.GigRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GigRequestController {

    private final GigRequestService service;

    @PostMapping
    public ResponseEntity<GigRequestDTO> create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateRequestDTO dto) {
        requireRole(role, "CLIENT");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId, dto));
    }

    @GetMapping
    public ResponseEntity<List<GigRequestDTO>> getOpenRequests() {
        return ResponseEntity.ok(service.getOpenRequests());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GigRequestDTO>> getMine(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        requireRole(role, "CLIENT");
        return ResponseEntity.ok(service.getMine(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GigRequestDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<GigRequestDTO> cancel(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {
        requireRole(role, "CLIENT");
        return ResponseEntity.ok(service.cancel(id, userId));
    }

    @PutMapping("/{id}/close")
    public ResponseEntity<GigRequestDTO> close(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {
        /* Ancienne route /close conservee pour compatibilite avec les camarades. */
        requireRole(role, "CLIENT");
        return ResponseEntity.ok(service.closeRequest(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {
        requireRole(role, "CLIENT");
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private void requireRole(String actualRole, String expectedRole) {
        if (!expectedRole.equalsIgnoreCase(actualRole)) {
            throw new RequestDomainException(HttpStatus.FORBIDDEN, "Role " + expectedRole.toLowerCase() + " requis");
        }
    }
}
