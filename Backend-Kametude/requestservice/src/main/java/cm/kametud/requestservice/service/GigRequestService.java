package cm.kametud.requestservice.service;

import cm.kametud.requestservice.client.IdentityClient;
import cm.kametud.requestservice.dto.CreateRequestDTO;
import cm.kametud.requestservice.dto.GigRequestDTO;
import cm.kametud.requestservice.enums.ProposalStatus;
import cm.kametud.requestservice.enums.RequestStatus;
import cm.kametud.requestservice.exception.RequestDomainException;
import cm.kametud.requestservice.model.GigRequest;
import cm.kametud.requestservice.model.RequestProposal;
import cm.kametud.requestservice.repository.GigRequestRepository;
import cm.kametud.requestservice.repository.RequestProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GigRequestService {

    private final GigRequestRepository repository;
    private final RequestProposalRepository proposalRepository;
    private final IdentityClient identityClient;

    @Transactional
    public GigRequestDTO create(UUID authenticatedClientId, CreateRequestDTO dto) {
        GigRequest request = new GigRequest();
        request.setTitle(dto.getTitle().trim());
        request.setDescription(dto.getDescription());
        request.setBudget(dto.getBudget());
        request.setCategory(dto.getCategory().trim());
        request.setLocation(dto.getLocation());
        LocalDate deadline = dto.getDeadline() == null ? LocalDate.now().plusDays(7) : dto.getDeadline();
        request.setDeadline(deadline.atTime(LocalTime.MAX));
        // Ancien code : clientId et clientName provenaient directement du DTO.
        request.setClientId(authenticatedClientId);
        request.setClientName(identityClient.getProfile(authenticatedClientId).displayName());
        request.setStatus(RequestStatus.OUVERT);
        return toDTO(repository.save(request));
    }

    @Transactional(readOnly = true)
    public List<GigRequestDTO> getOpenRequests() {
        return repository.findByStatusOrderByCreatedAtDesc(RequestStatus.OUVERT).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GigRequestDTO> getMine(UUID clientId) {
        return repository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public GigRequestDTO getById(UUID id) {
        return toDTO(findOrThrow(id));
    }

    @Transactional
    public GigRequestDTO cancel(UUID id, UUID clientId) {
        GigRequest request = findOwnedRequest(id, clientId);
        if (request.getStatus() != RequestStatus.OUVERT) {
            throw new RequestDomainException(HttpStatus.CONFLICT, "Cette demande ne peut plus etre annulee");
        }
        request.setStatus(RequestStatus.ANNULE);
        List<RequestProposal> proposals = proposalRepository.findByRequestIdAndStatus(id, ProposalStatus.EN_ATTENTE);
        proposals.forEach(proposal -> proposal.setStatus(ProposalStatus.REFUSEE));
        proposalRepository.saveAll(proposals);
        return toDTO(repository.save(request));
    }

    @Transactional
    public GigRequestDTO closeRequest(UUID id, UUID clientId) {
        GigRequest request = findOwnedRequest(id, clientId);
        request.setStatus(RequestStatus.FERME);
        return toDTO(repository.save(request));
    }

    @Transactional
    public void delete(UUID id, UUID clientId) {
        GigRequest request = findOwnedRequest(id, clientId);
        if (request.getStatus() == RequestStatus.ASSIGNE) {
            throw new RequestDomainException(HttpStatus.CONFLICT, "Une demande assignee ne peut pas etre supprimee");
        }
        repository.delete(request);
    }

    GigRequest findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RequestDomainException(HttpStatus.NOT_FOUND, "Demande non trouvee"));
    }

    private GigRequest findOwnedRequest(UUID id, UUID clientId) {
        GigRequest request = findOrThrow(id);
        if (!request.getClientId().equals(clientId)) {
            throw new RequestDomainException(HttpStatus.FORBIDDEN, "Cette demande appartient a un autre client");
        }
        return request;
    }

    GigRequestDTO toDTO(GigRequest request) {
        GigRequestDTO dto = new GigRequestDTO();
        dto.setId(request.getId());
        dto.setTitle(request.getTitle());
        dto.setDescription(request.getDescription());
        dto.setBudget(request.getBudget());
        dto.setCategory(request.getCategory());
        dto.setLocation(request.getLocation());
        dto.setDeadline(request.getDeadline());
        dto.setStatus(toApiStatus(request.getStatus()));
        dto.setClientId(request.getClientId());
        dto.setClientName(request.getClientName());
        dto.setAcceptedProposalId(request.getAcceptedProposalId());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setUpdatedAt(request.getUpdatedAt());
        dto.setProposalsCount((int) proposalRepository.countByRequestId(request.getId()));
        return dto;
    }

    static String toApiStatus(RequestStatus status) {
        return switch (status) {
            case OUVERT -> "open";
            case ASSIGNE -> "assigned";
            case FERME -> "closed";
            case ANNULE -> "cancelled";
        };
    }
}
