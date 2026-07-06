package cm.kametud.requestservice.client;

import java.util.UUID;

public record ProposalOrderCommand(
        UUID sourceRequestId,
        UUID sourceProposalId,
        UUID clientId,
        String clientName,
        UUID studentId,
        String studentName,
        String title,
        String description,
        Double budget,
        Integer deliveryDays
) {
}
