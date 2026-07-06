package com.kametude.payment.service;

import com.kametude.payment.client.BusinessClient;
import com.kametude.payment.client.BusinessOrderContext;
import com.kametude.payment.client.IdentityClient;
import com.kametude.payment.client.SupportClient;
import com.kametude.payment.dto.PaymentRequestDTO;
import com.kametude.payment.dto.PaymentResponseDTO;
import com.kametude.payment.entity.PaymentTransaction;
import com.kametude.payment.exception.InvalidTransactionStatusException;
import com.kametude.payment.exception.PaymentException;
// Ancien import direct devenu inutile : PaymentTransactionManager centralise cette exception.
// import com.kametude.payment.exception.TransactionNotFoundException;
import com.kametude.payment.provider.PaymentProvider;
// Ancien acces direct au repository remplace par PaymentTransactionManager :
// il persiste chaque reservation dans une transaction courte avant l'appel MeSomb.
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");
    private static final Set<String> NON_PAYABLE = Set.of("cancelled", "refunded", "rejected");

    private final PaymentProvider paymentProvider;
    private final PaymentTransactionManager transactionManager;
    private final BusinessClient businessClient;
    private final IdentityClient identityClient;
    private final SupportClient supportClient;

    public PaymentResponseDTO initiate(UUID authenticatedClientId, String role, PaymentRequestDTO request) {
        requireRole(role, "CLIENT");
        BusinessOrderContext order = businessClient.getOrder(request.getOrderId());
        if (!order.clientId().equals(authenticatedClientId)) {
            throw new PaymentException("PAYMENT_FORBIDDEN", "Cette commande appartient a un autre client");
        }
        if (NON_PAYABLE.contains(order.status())) {
            throw new InvalidTransactionStatusException(order.status());
        }
        String payerPhone = normalizePhone(request.getPhone(), "INVALID_PAYER_PHONE",
                "Numero Mobile Money du client invalide");
        String rawSellerPhone = identityClient.getPayoutProfile(order.studentId()).phone();
        if (rawSellerPhone == null || rawSellerPhone.isBlank()) {
            throw new PaymentException("SELLER_PHONE_MISSING",
                    "L'etudiant doit configurer son numero Mobile Money avant le paiement");
        }
        String sellerPhone = normalizePhone(rawSellerPhone, "INVALID_SELLER_PHONE",
                "Numero Mobile Money de l'etudiant invalide");
        BigDecimal amount = BigDecimal.valueOf(order.budget()).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new PaymentException("INVALID_AMOUNT", "Le montant de la commande doit etre positif");
        }
        BigDecimal commission = amount.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);

        PaymentReservation reservation;
        try {
            reservation = transactionManager.reserveCollection(order.id(), amount, commission,
                    payerPhone, sellerPhone, paymentProvider.getProviderName());
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Une autre instance a reserve cette meme commande entre-temps.
            reservation = new PaymentReservation(transactionManager.findByOrder(order.id()), false);
        }
        PaymentTransaction transaction = reservation.transaction();
        if (!reservation.executeProviderCall()) {
            synchronizeHeldOrder(transaction);
            return toResponse(transaction, "Transaction deja initialisee ou en cours");
        }

        // Ancien code : amount et sellerPhone provenaient directement du corps JSON.
        PaymentRequestDTO providerRequest = PaymentRequestDTO.builder()
                .orderId(order.id()).amount(amount.doubleValue()).phone(payerPhone).sellerPhone(sellerPhone)
                .idempotencyKey(transaction.getCollectionKey()).build();
        try {
            PaymentResponseDTO providerResponse = paymentProvider.initierPaiement(providerRequest);
            String providerStatus = providerResponse.getStatus() == null ? "FAILED" : providerResponse.getStatus().toUpperCase();
            String persistedStatus = ("HELD".equals(providerStatus) || "SUCCESS".equals(providerStatus))
                    ? "HELD" : ("PENDING".equals(providerStatus) ? "PENDING" : "FAILED");
            PaymentTransaction saved = transactionManager.recordCollectionResult(transaction.getId(), persistedStatus,
                    providerResponse.getExternalReference(), "FAILED".equals(persistedStatus) ? providerResponse.getMessage() : null);
            synchronizeHeldOrder(saved);
            return toResponse(saved, providerResponse.getMessage());
        } catch (RuntimeException providerFailure) {
            transactionManager.markUnknown(transaction.getId(), "COLLECTION_UNKNOWN", providerFailure.getMessage());
            throw providerFailure;
        }
    }

    public PaymentResponseDTO verify(UUID orderId, UUID userId, String role) {
        PaymentTransaction transaction = findByOrder(orderId);
        BusinessOrderContext order = businessClient.getOrder(orderId);
        ensureParticipantOrStaff(order, userId, role);
        if (transaction.getExternalReference() == null || "RELEASED".equals(transaction.getStatus())) {
            return toResponse(transaction, "Statut local");
        }
        String providerStatus = paymentProvider.verifierStatut(transaction.getExternalReference());
        if ("SUCCESS".equalsIgnoreCase(providerStatus) || "HELD".equalsIgnoreCase(providerStatus)) {
            transaction = transactionManager.updateVerifiedStatus(transaction.getId(), "HELD");
        } else if ("FAILED".equalsIgnoreCase(providerStatus)) {
            transaction = transactionManager.updateVerifiedStatus(transaction.getId(), "FAILED");
        } else {
            transaction = transactionManager.updateVerifiedStatus(transaction.getId(), "PENDING");
        }
        synchronizeHeldOrder(transaction);
        return toResponse(transaction, "Statut fournisseur actualise");
    }

    public PaymentResponseDTO releaseByOrder(UUID orderId, UUID userId, String role) {
        BusinessOrderContext order = businessClient.getOrder(orderId);
        boolean staff = "ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role);
        if (!staff && !("CLIENT".equalsIgnoreCase(role) && order.clientId().equals(userId))) {
            throw new PaymentException("PAYMENT_FORBIDDEN", "Liberation des fonds non autorisee");
        }
        if (!"completed".equalsIgnoreCase(order.status())) {
            throw new InvalidTransactionStatusException("ORDER_" + order.status());
        }
        return release(order);
    }

    public PaymentResponseDTO releaseAutomatically(UUID orderId) {
        BusinessOrderContext order = businessClient.getOrder(orderId);
        if (!"completed".equalsIgnoreCase(order.status())) {
            throw new InvalidTransactionStatusException("ORDER_" + order.status());
        }
        return release(order);
    }

    private PaymentResponseDTO release(BusinessOrderContext order) {
        PaymentReservation reservation = transactionManager.reservePayout(order.id());
        PaymentTransaction transaction = reservation.transaction();
        if (!reservation.executeProviderCall()) {
            return toResponse(transaction, "Versement deja effectue ou en cours de reconciliation");
        }
        BigDecimal netAmount = transaction.getAmount().subtract(transaction.getCommission());
        try {
            String payoutReference = paymentProvider.libererFonds(
                    transaction.getSellerPhone(), netAmount.doubleValue(), transaction.getPayoutKey());
            PaymentTransaction saved = transactionManager.recordPayoutSuccess(transaction.getId(), payoutReference);
            try {
                supportClient.notify(order.studentId(), "Paiement recu",
                        netAmount.toPlainString() + " FCFA ont ete liberes", "PAYMENT_CONFIRMED", "/mes-missions");
            } catch (RuntimeException notificationFailure) {
                // Le versement est deja confirme : une panne de notification ne doit jamais le rejouer.
                log.warn("Versement {} confirme mais notification differee", order.id());
            }
            return toResponse(saved, "Fonds liberes au prestataire");
        } catch (RuntimeException providerFailure) {
            transactionManager.markUnknown(transaction.getId(), "PAYOUT_UNKNOWN", providerFailure.getMessage());
            throw providerFailure;
        }
    }

    public PaymentResponseDTO getByOrder(UUID orderId, UUID userId, String role) {
        BusinessOrderContext order = businessClient.getOrder(orderId);
        ensureParticipantOrStaff(order, userId, role);
        return toResponse(findByOrder(orderId), "Transaction trouvee");
    }

    private PaymentTransaction findByOrder(UUID orderId) {
        return transactionManager.findByOrder(orderId);
    }

    private void ensureParticipantOrStaff(BusinessOrderContext order, UUID userId, String role) {
        boolean participant = order.clientId().equals(userId) || order.studentId().equals(userId);
        boolean staff = "ADMIN".equalsIgnoreCase(role) || "MODERATOR".equalsIgnoreCase(role);
        if (!participant && !staff) throw new PaymentException("PAYMENT_FORBIDDEN", "Transaction non autorisee");
    }

    private void requireRole(String role, String expected) {
        if (!expected.equalsIgnoreCase(role)) throw new PaymentException("PAYMENT_FORBIDDEN", "Role client requis");
    }

    private void synchronizeHeldOrder(PaymentTransaction transaction) {
        if (!"HELD".equals(transaction.getStatus())) return;
        try {
            businessClient.markPaymentHeld(transaction.getOrderId());
        } catch (RuntimeException exception) {
            // La transaction MeSomb reste enregistree et tout nouvel appel retentera
            // la synchronisation sans effectuer une seconde collecte.
            log.warn("Paiement {} detenu mais synchronisation Business differee", transaction.getOrderId());
        }
    }

    private String normalizePhone(String value, String errorCode, String errorMessage) {
        String phone = value == null ? "" : value.replaceAll("\\D", "");
        if (phone.length() == 9) phone = "237" + phone;
        if (!phone.matches("2376\\d{8}")) {
            // Ancienne erreur commune : INVALID_PHONE ne permettait pas de
            // distinguer le payeur du beneficiaire.
            throw new PaymentException(errorCode, errorMessage);
        }
        return phone;
    }

    private PaymentResponseDTO toResponse(PaymentTransaction transaction, String message) {
        BigDecimal net = transaction.getAmount().subtract(transaction.getCommission());
        return PaymentResponseDTO.builder().transactionId(transaction.getId()).orderId(transaction.getOrderId())
                .status(transaction.getStatus()).externalReference(transaction.getExternalReference())
                .commission(transaction.getCommission().doubleValue()).netAmount(net.doubleValue())
                .payoutReference(transaction.getPayoutReference()).message(message).build();
    }

    /* Anciens appels traiterPaiement(request avec montant) et libererPaiement(transactionId)
       remplaces par des operations liees a la commande et a l'identite JWT. */
}
