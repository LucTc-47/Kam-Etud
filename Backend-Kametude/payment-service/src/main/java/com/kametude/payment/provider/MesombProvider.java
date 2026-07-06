package com.kametude.payment.provider;

import com.hachther.mesomb.operations.PaymentOperation;
import com.hachther.mesomb.MeSomb;
import com.hachther.mesomb.util.RandomGenerator;
import com.hachther.mesomb.models.TransactionResponse;
import com.hachther.mesomb.models.Transaction;
import com.hachther.mesomb.exceptions.InvalidClientRequestException;
import com.hachther.mesomb.exceptions.PermissionDeniedException;
import com.hachther.mesomb.exceptions.ServerException;
import com.hachther.mesomb.exceptions.ServiceNotFoundException;
import com.kametude.payment.dto.PaymentRequestDTO;
import com.kametude.payment.dto.PaymentResponseDTO;
import com.kametude.payment.exception.PaymentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mesomb", matchIfMissing = true)
@Slf4j
public class MesombProvider implements PaymentProvider {

    @Value("${mesomb.application-key}")
    private String applicationKey;

    @Value("${mesomb.access-key}")
    private String accessKey;

    @Value("${mesomb.secret-key}")
    private String secretKey;

    @Value("${mesomb.request-timeout-seconds:30}")
    private int requestTimeoutSeconds;

    @Value("${mesomb.max-network-retries:0}")
    private int maxNetworkRetries;

    @PostConstruct
    void configureSdk() {
        if (isBlank(applicationKey) || isBlank(accessKey) || isBlank(secretKey)) {
            throw new IllegalStateException("Les cles MeSomb doivent etre fournies par l'environnement");
        }
        MeSomb.requestTimeout = requestTimeoutSeconds;
        MeSomb.maxNetworkRetries = maxNetworkRetries;
    }

    private PaymentOperation client() {
        return new PaymentOperation(applicationKey, accessKey, secretKey);
    }

    @Override
    public PaymentResponseDTO initierPaiement(PaymentRequestDTO requestDTO) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", requestDTO.getAmount());
            payload.put("service", detecterOperateur(requestDTO.getPhone()));
            payload.put("payer", extraireNumeroNettoye(requestDTO.getPhone()));
            payload.put("country", "CM");
            payload.put("currency", "XAF");
            payload.put("nonce", RandomGenerator.nonce());
            payload.put("trxID", requestDTO.getIdempotencyKey());

            TransactionResponse response = client().makeCollect(payload);

            /* Ancien bloc System.out de debug MeSomb retire : il exposait les
               references de transaction dans les journaux de production. */

            PaymentResponseDTO dto = new PaymentResponseDTO();
            dto.setTransactionId(UUID.randomUUID());
            dto.setCommission(requestDTO.getAmount() * 0.10);
            dto.setNetAmount(requestDTO.getAmount() * 0.90);

            if (response.isTransactionSuccess()) {
                dto.setStatus("HELD");
                dto.setExternalReference(response.reference);
                dto.setMessage(response.message != null
                    ? response.message
                    : "Paiement de " + requestDTO.getAmount() + " XAF collecté avec succès (MeSomb).");
            } else {
                dto.setStatus("FAILED");
                dto.setMessage(response.message != null ? response.message : "Échec de la collecte MeSomb.");
            }
            return dto;

        } catch (InvalidClientRequestException | PermissionDeniedException
                 | ServerException | ServiceNotFoundException e) {
            log.warn("Collecte MeSomb refusee pour {} ({})", requestDTO.getOrderId(), e.getClass().getSimpleName());
            throw new PaymentException("PAYMENT_PROVIDER_ERROR",
                "MeSomb a refuse la collecte");
        } catch (Exception e) {
            log.error("MeSomb injoignable pendant la collecte {}", requestDTO.getOrderId(), e);
            throw new PaymentException("PAYMENT_PROVIDER_UNREACHABLE",
                "Impossible de joindre MeSomb");
        }
    }

    @Override
    public String verifierStatut(String externalReference) {
        try {
            Transaction[] transactions = client().getTransactions(new String[]{externalReference});
            if (transactions.length == 0) {
                throw new PaymentException("TRANSACTION_NOT_FOUND",
                    "Aucune transaction MeSomb trouvée pour la référence : " + externalReference);
            }
            return transactions[0].status;
        } catch (PaymentException exception) {
            throw exception;
        } catch (InvalidClientRequestException | PermissionDeniedException
                 | ServerException | ServiceNotFoundException e) {
            log.warn("Verification MeSomb refusee ({})", e.getClass().getSimpleName());
            throw new PaymentException("PAYMENT_PROVIDER_ERROR",
                "MeSomb a refuse la verification");
        } catch (Exception e) {
            log.error("MeSomb injoignable pendant une verification", e);
            throw new PaymentException("PAYMENT_PROVIDER_UNREACHABLE",
                "Impossible de joindre MeSomb pour verifier le statut");
        }
    }

    @Override
    public String libererFonds(String sellerPhone, Double amount, String orderReference) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", amount);
            payload.put("service", detecterOperateur(sellerPhone));
            payload.put("receiver", extraireNumeroNettoye(sellerPhone));
            payload.put("country", "CM");
            payload.put("currency", "XAF");
            payload.put("nonce", RandomGenerator.nonce());
            payload.put("trxID", orderReference);

            TransactionResponse response = client().makeDeposit(payload);

            if (!response.isTransactionSuccess()) {
                throw new PaymentException("PAYMENT_PROVIDER_ERROR",
                    "Virement MeSomb refusé pour la commande " + orderReference
                    + (response.message != null ? " : " + response.message : ""));
            }
            return response.reference;

        } catch (PaymentException exception) {
            throw exception;
        } catch (InvalidClientRequestException | PermissionDeniedException
                 | ServerException | ServiceNotFoundException e) {
            log.warn("Versement MeSomb refuse pour {} ({})", orderReference, e.getClass().getSimpleName());
            throw new PaymentException("PAYMENT_PROVIDER_ERROR",
                "MeSomb a refuse le versement au prestataire");
        } catch (Exception e) {
            log.error("MeSomb injoignable pendant le versement {}", orderReference, e);
            throw new PaymentException("PAYMENT_PROVIDER_UNREACHABLE",
                "Impossible de joindre MeSomb pour le versement");
        }
    }

    @Override
    public String getProviderName() {
        return "MeSomb";
    }

    private String detecterOperateur(String phone) {
        String clean = extraireNumeroNettoye(phone);
        if (clean.startsWith("237")) clean = clean.substring(3);
        if (clean.startsWith("69") || clean.matches("^65[4-9].*")) return "ORANGE";
        return "MTN";
    }

    private String extraireNumeroNettoye(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
