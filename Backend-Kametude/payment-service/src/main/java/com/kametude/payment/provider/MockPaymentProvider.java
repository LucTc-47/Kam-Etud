package com.kametude.payment.provider;

import com.kametude.payment.dto.PaymentRequestDTO;
import com.kametude.payment.dto.PaymentResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fournisseur de paiement strictement local : aucune requete reseau et aucun
 * mouvement d'argent ne sont effectues. Il conserve le parcours normal du
 * PaymentService afin de tester le sequestre et la liberation des fonds.
 *
 * Activation : PAYMENT_PROVIDER=mock
 */
@Component
@Primary
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock")
@Slf4j
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public PaymentResponseDTO initierPaiement(PaymentRequestDTO requestDTO) {
        String reference = "MOCK-COLLECT-" + requestDTO.getIdempotencyKey();
        log.info("Simulation locale de la collecte pour la commande {}", requestDTO.getOrderId());

        return PaymentResponseDTO.builder()
                .transactionId(UUID.randomUUID())
                .orderId(requestDTO.getOrderId())
                .status("HELD")
                .externalReference(reference)
                .message("Paiement simule et place sous sequestre local")
                .commission(requestDTO.getAmount() * 0.10)
                .netAmount(requestDTO.getAmount() * 0.90)
                .build();
    }

    @Override
    public String verifierStatut(String externalReference) {
        log.info("Simulation locale de la verification {}", externalReference);
        return "HELD";
    }

    @Override
    public String libererFonds(String sellerPhone, Double amount, String orderReference) {
        log.info("Simulation locale du versement {} pour {} FCFA", orderReference, amount);
        return "MOCK-PAYOUT-" + orderReference;
    }

    @Override
    public String getProviderName() {
        return "MockPayment";
    }
}
