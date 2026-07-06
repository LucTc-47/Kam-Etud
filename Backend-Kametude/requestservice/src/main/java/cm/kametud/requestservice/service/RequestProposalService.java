package cm.kametud.requestservice.service;

import cm.kametud.requestservice.client.IdentityClient;
import cm.kametud.requestservice.client.BusinessClient;
import cm.kametud.requestservice.client.ProposalOrderCommand;
import cm.kametud.requestservice.client.SupportClient;
import cm.kametud.requestservice.dto.CreateProposalDTO;
import cm.kametud.requestservice.dto.ProposalDTO;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestProposalService {

    private final RequestProposalRepository repository;
    private final GigRequestRepository gigRequestRepository;
    private final IdentityClient identityClient;
    private final BusinessClient businessClient;
    private final SupportClient supportClient;

    @Transactional
    public ProposalDTO create(UUID authenticatedStudentId, CreateProposalDTO dto) {
        GigRequest request = findRequest(dto.getRequestId());
        if (request.getStatus() != RequestStatus.OUVERT) {
            throw new RequestDomainException(HttpStatus.CONFLICT, "Cette demande n'est plus ouverte");
        }
        if (repository.existsByRequestIdAndStudentId(dto.getRequestId(), authenticatedStudentId)) {
            throw new RequestDomainException(HttpStatus.CONFLICT, "Vous avez deja propose vos services");
        }
        if (!identityClient.getStudentStatus(authenticatedStudentId).canPropose()) {
            throw new RequestDomainException(HttpStatus.FORBIDDEN, "Verification KYC requise pour proposer");
        }

        RequestProposal proposal = new RequestProposal();
        proposal.setRequestId(dto.getRequestId());
        // Ancien code : studentId et studentName provenaient du DTO.
        proposal.setStudentId(authenticatedStudentId);
        proposal.setStudentName(identityClient.getProfile(authenticatedStudentId).displayName());
        proposal.setMessage(dto.getMessage());
        proposal.setPrice(dto.getPrice());
        proposal.setDeliveryDays(dto.getDeliveryDays());
        proposal.setStatus(ProposalStatus.EN_ATTENTE);
        RequestProposal saved = repository.save(proposal);
        supportClient.notify(request.getClientId(), "Nouvelle proposition",
                proposal.getStudentName() + " a propose ses services", "PROPOSAL",
                "/demandes/" + request.getId());
        return toDTO(saved, request);
    }

    @Transactional(readOnly = true)
    public List<ProposalDTO> getByRequestId(UUID requestId, UUID userId, String role) {
        GigRequest request = findRequest(requestId);
        List<RequestProposal> proposals;
        if ("CLIENT".equalsIgnoreCase(role)) {
            if (!request.getClientId().equals(userId)) {
                throw new RequestDomainException(HttpStatus.FORBIDDEN, "Demande d'un autre client");
            }
            proposals = repository.findByRequestIdOrderByCreatedAtDesc(requestId);
        } else if ("STUDENT".equalsIgnoreCase(role)) {
            proposals = repository.findByRequestIdAndStudentId(requestId, userId);
        } else if ("ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role)) {
            proposals = repository.findByRequestIdOrderByCreatedAtDesc(requestId);
        } else {
            throw new RequestDomainException(HttpStatus.FORBIDDEN, "Role non autorise");
        }
        return proposals.stream().map(proposal -> toDTO(proposal, request)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProposalDTO> getMine(UUID studentId) {
        return repository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(proposal -> toDTO(proposal, findRequest(proposal.getRequestId())))
                .toList();
    }

    @Transactional
    public ProposalDTO acceptProposal(UUID proposalId, UUID authenticatedClientId) {
        RequestProposal proposal = findProposal(proposalId);
        GigRequest request = findRequest(proposal.getRequestId());
        ensureOwner(request, authenticatedClientId);
        if (request.getStatus() != RequestStatus.OUVERT || proposal.getStatus() != ProposalStatus.EN_ATTENTE) {
            throw new RequestDomainException(HttpStatus.CONFLICT, "Cette proposition n'est plus disponible");
        }

        List<RequestProposal> others = repository.findByRequestIdAndStatus(
                proposal.getRequestId(), ProposalStatus.EN_ATTENTE);
        others.stream()
                .filter(value -> !value.getId().equals(proposalId))
                .forEach(value -> value.setStatus(ProposalStatus.REFUSEE));
        repository.saveAll(others);

        proposal.setStatus(ProposalStatus.ACCEPTEE);
        RequestProposal saved = repository.save(proposal);
        request.setStatus(RequestStatus.ASSIGNE);
        request.setAcceptedProposalId(proposalId);
        gigRequestRepository.save(request);

        // Ancien flux React : insertion directe dans la table Supabase orders.
        // Request Service transmet maintenant un contexte fiable et idempotent a Business Service.
        businessClient.createOrder(new ProposalOrderCommand(
                request.getId(), proposal.getId(), request.getClientId(), request.getClientName(),
                proposal.getStudentId(), proposal.getStudentName(), request.getTitle(),
                request.getDescription(), proposal.getPrice(), proposal.getDeliveryDays()));
        return toDTO(saved, request);
    }

    @Transactional
    public void rejectProposal(UUID proposalId, UUID authenticatedClientId) {
        RequestProposal proposal = findProposal(proposalId);
        GigRequest request = findRequest(proposal.getRequestId());
        ensureOwner(request, authenticatedClientId);
        proposal.setStatus(ProposalStatus.REFUSEE);
        repository.save(proposal);
    }

    @Transactional
    public void delete(UUID id, UUID authenticatedStudentId) {
        RequestProposal proposal = findProposal(id);
        if (!proposal.getStudentId().equals(authenticatedStudentId)) {
            throw new RequestDomainException(HttpStatus.FORBIDDEN, "Cette proposition appartient a un autre etudiant");
        }
        if (proposal.getStatus() != ProposalStatus.EN_ATTENTE) {
            throw new RequestDomainException(HttpStatus.CONFLICT, "Cette proposition ne peut plus etre supprimee");
        }
        repository.delete(proposal);
    }

    private RequestProposal findProposal(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RequestDomainException(HttpStatus.NOT_FOUND, "Proposition non trouvee"));
    }

    private GigRequest findRequest(UUID id) {
        return gigRequestRepository.findById(id)
                .orElseThrow(() -> new RequestDomainException(HttpStatus.NOT_FOUND, "Demande non trouvee"));
    }

    private void ensureOwner(GigRequest request, UUID clientId) {
        if (!request.getClientId().equals(clientId)) {
            throw new RequestDomainException(HttpStatus.FORBIDDEN, "Cette demande appartient a un autre client");
        }
    }

    private ProposalDTO toDTO(RequestProposal proposal, GigRequest request) {
        ProposalDTO dto = new ProposalDTO();
        dto.setId(proposal.getId());
        dto.setRequestId(proposal.getRequestId());
        dto.setStudentId(proposal.getStudentId());
        dto.setStudentName(proposal.getStudentName());
        dto.setMessage(proposal.getMessage());
        dto.setPrice(proposal.getPrice());
        dto.setDeliveryDays(proposal.getDeliveryDays());
        dto.setStatus(toApiStatus(proposal.getStatus()));
        dto.setCreatedAt(proposal.getCreatedAt());
        dto.setUpdatedAt(proposal.getUpdatedAt());
        dto.setRequestTitle(request.getTitle());
        dto.setRequestBudget(request.getBudget());
        dto.setRequestStatus(GigRequestService.toApiStatus(request.getStatus()));
        return dto;
    }

    private String toApiStatus(ProposalStatus status) {
        return switch (status) {
            case EN_ATTENTE -> "pending";
            case ACCEPTEE -> "accepted";
            case REFUSEE -> "rejected";
        };
    }
}
