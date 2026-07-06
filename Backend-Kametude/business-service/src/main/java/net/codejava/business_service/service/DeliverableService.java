package net.codejava.business_service.service;

import lombok.RequiredArgsConstructor;
import net.codejava.business_service.dto.DeliverableRequest;
import net.codejava.business_service.dto.DeliverableResponse;
import net.codejava.business_service.dto.OrderUpdateRequest;
import net.codejava.business_service.entity.Deliverable;
import net.codejava.business_service.repository.DeliverableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliverableService {
    private final DeliverableRepository deliverableRepository;
    private final OrderService orderService;
    private final OrderWorkflowService workflowService;

    @Transactional
    public DeliverableResponse submit(UUID userId, String role, DeliverableRequest request) {
        OrderUpdateRequest update = new OrderUpdateRequest();
        update.setStatus("delivered");
        update.setDeliverableUrl(request.getFileUrl());
        update.setDeliverableNote(request.getDescription());
        workflowService.update(request.getOrderId(), userId, role, update);
        Deliverable deliverable = Deliverable.builder()
                .orderId(request.getOrderId()).fileUrl(request.getFileUrl())
                .description(request.getDescription()).build();
        return toResponse(deliverableRepository.save(deliverable));
    }

    @Transactional(readOnly = true)
    public List<DeliverableResponse> getByOrder(UUID orderId, UUID userId, String role) {
        orderService.getOrderById(orderId, userId, role);
        return deliverableRepository.findByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    private DeliverableResponse toResponse(Deliverable value) {
        return DeliverableResponse.builder().id(value.getId()).orderId(value.getOrderId())
                .fileUrl(value.getFileUrl()).description(value.getDescription())
                .submittedAt(value.getSubmittedAt()).build();
    }
}
