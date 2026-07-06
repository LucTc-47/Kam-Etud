package cm.kametud.requestservice.controller;

import cm.kametud.requestservice.dto.CreateProposalDTO;
import cm.kametud.requestservice.dto.ProposalDTO;
import cm.kametud.requestservice.exception.RequestDomainException;
import cm.kametud.requestservice.service.RequestProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RequestProposalController {

    private final RequestProposalService service;

    @PostMapping
    public ResponseEntity<ProposalDTO> create(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateProposalDTO dto) {
        requireRole(role, "STUDENT");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId, dto));
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<List<ProposalDTO>> getByRequest(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.getByRequestId(requestId, userId, role));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ProposalDTO>> getMine(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        requireRole(role, "STUDENT");
        return ResponseEntity.ok(service.getMine(userId));
    }

    /* Ancienne route non securisee retiree : GET /student/{studentId}.
       Elle permettait de lire les propositions de n'importe quel etudiant. */

    @PutMapping("/{id}/accept")
    public ResponseEntity<ProposalDTO> accept(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {
        requireRole(role, "CLIENT");
        return ResponseEntity.ok(service.acceptProposal(id, userId));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {
        requireRole(role, "CLIENT");
        service.rejectProposal(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {
        requireRole(role, "STUDENT");
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private void requireRole(String actualRole, String expectedRole) {
        if (!expectedRole.equalsIgnoreCase(actualRole)) {
            throw new RequestDomainException(HttpStatus.FORBIDDEN, "Role " + expectedRole.toLowerCase() + " requis");
        }
    }
}
