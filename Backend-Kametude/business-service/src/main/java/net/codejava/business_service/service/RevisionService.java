package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.OrderUpdateRequest;
import net.codejava.business_service.dto.RevisionRequest;
import net.codejava.business_service.dto.RevisionResponse;
import net.codejava.business_service.entity.Revision;
import net.codejava.business_service.repository.RevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevisionService {
    private final RevisionRepository revisionRepository;
    private final OrderService orderService;
    private final OrderWorkflowService workflowService;

    @Transactional
    public RevisionResponse create(UUID userId, String role, RevisionRequest request) {
        OrderUpdateRequest update = new OrderUpdateRequest();
        update.setStatus("revision_requested");
        workflowService.update(request.getOrderId(), userId, role, update);
        Revision revision = Revision.builder().orderId(request.getOrderId())
                .reason(request.getReason()).build();
        return toResponse(revisionRepository.save(revision));
    }

    @Transactional(readOnly = true)
    public List<RevisionResponse> getByOrder(UUID orderId, UUID userId, String role) {
        orderService.getOrderById(orderId, userId, role);
        return revisionRepository.findByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    private RevisionResponse toResponse(Revision value) {
        return RevisionResponse.builder().id(value.getId()).orderId(value.getOrderId())
                .reason(value.getReason()).requestedAt(value.getRequestedAt()).build();
    }
}
