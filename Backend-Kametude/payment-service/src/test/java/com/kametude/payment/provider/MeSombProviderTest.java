package com.kametude.payment.provider;

import com.hachther.mesomb.models.Transaction;
import com.hachther.mesomb.models.TransactionResponse;
import com.hachther.mesomb.operations.PaymentOperation;
import com.kametude.payment.dto.PaymentRequestDTO;
import com.kametude.payment.dto.PaymentResponseDTO;
import com.kametude.payment.exception.PaymentException;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Tests unitaires — MesombProvider")
class MesombProviderTest {

    private MesombProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new MesombProvider();
        setField("applicationKey", "app-key-test");
        setField("accessKey", "access-key-test");
        setField("secretKey", "secret-key-test");
    }

    private void setField(String name, String value) throws Exception {
        Field field = MesombProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(provider, value);
    }

    @SuppressWarnings("unchecked")
    private TransactionResponse buildTransactionResponse(boolean success, String status, String reference, String message) {
        JSONObject transactionJson = new JSONObject();
        transactionJson.put("pk", reference);
        transactionJson.put("status", status);

        JSONObject json = new JSONObject();
        json.put("success", success);
        json.put("message", message);
        json.put("redirect", null);
        json.put("transaction", transactionJson);
        json.put("reference", reference);
        json.put("status", status);

        return new TransactionResponse(json);
    }

    @SuppressWarnings("unchecked")
    private Transaction buildTransaction(String pk, String status) {
        JSONObject json = new JSONObject();
        json.put("pk", pk);
        json.put("status", status);
        json.put("type", "COLLECT");
        json.put("amount", 6000.0);
        json.put("fees", 0.0);
        json.put("b_party", "237670256547");
        json.put("service", "MTN");
        json.put("reference", pk);
        json.put("country", "CM");
        json.put("currency", "XAF");
        return new Transaction(json);
    }

    // ---- initierPaiement ----

    @Test
    @DisplayName("initierPaiement — doit retourner HELD avec commission 10% quand MeSomb confirme")
    void initierPaiement_doitRetournerHeldQuandSucces() throws Exception {
        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .amount(6000.0)
                .phone("237670256547")
                .sellerPhone("237655547896")
                .build();

        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.makeCollect(anyMap()))
                        .thenReturn(buildTransactionResponse(true, "SUCCESS", "461328", "Collecte réussie")))) {

            PaymentResponseDTO result = provider.initierPaiement(requestDTO);

            assertThat(result.getStatus()).isEqualTo("HELD");
            assertThat(result.getExternalReference()).isEqualTo("461328");
            assertThat(result.getCommission()).isEqualTo(600.0);
            assertThat(result.getNetAmount()).isEqualTo(5400.0);
        }
    }

    @Test
    @DisplayName("initierPaiement — doit retourner FAILED quand la transaction MeSomb échoue")
    void initierPaiement_doitRetournerFailedQuandEchec() throws Exception {
        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .amount(6000.0)
                .phone("237670256547")
                .sellerPhone("237655547896")
                .build();

        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.makeCollect(anyMap()))
                        .thenReturn(buildTransactionResponse(true, "FAILED", null, "Solde insuffisant")))) {

            PaymentResponseDTO result = provider.initierPaiement(requestDTO);

            assertThat(result.getStatus()).isEqualTo("FAILED");
            assertThat(result.getMessage()).isEqualTo("Solde insuffisant");
        }
    }

    @Test
    @DisplayName("initierPaiement — doit détecter l'opérateur ORANGE pour un préfixe 655-659")
    void initierPaiement_doitDetecterOrange() throws Exception {
        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .amount(1000.0)
                .phone("237655547896")
                .sellerPhone("237670256547")
                .build();

        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.makeCollect(anyMap()))
                        .thenReturn(buildTransactionResponse(true, "SUCCESS", "ref1", "ok")))) {

            provider.initierPaiement(requestDTO);

            PaymentOperation mock = mocked.constructed().get(0);
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
            verify(mock).makeCollect(captor.capture());
            assertThat(captor.getValue().get("service")).isEqualTo("ORANGE");
        }
    }

    @Test
    @DisplayName("initierPaiement — doit détecter l'opérateur MTN par défaut")
    void initierPaiement_doitDetecterMtn() throws Exception {
        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .amount(1000.0)
                .phone("237670256547")
                .sellerPhone("237655547896")
                .build();

        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.makeCollect(anyMap()))
                        .thenReturn(buildTransactionResponse(true, "SUCCESS", "ref1", "ok")))) {

            provider.initierPaiement(requestDTO);

            PaymentOperation mock = mocked.constructed().get(0);
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
            verify(mock).makeCollect(captor.capture());
            assertThat(captor.getValue().get("service")).isEqualTo("MTN");
        }
    }

    // ---- verifierStatut ----

    @Test
    @DisplayName("verifierStatut — doit retourner le statut de la transaction trouvée")
    void verifierStatut_doitRetournerStatut() throws Exception {
        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.getTransactions(any(String[].class)))
                        .thenReturn(new Transaction[]{buildTransaction("461328", "SUCCESS")}))) {

            String status = provider.verifierStatut("461328");

            assertThat(status).isEqualTo("SUCCESS");
        }
    }

    @Test
    @DisplayName("verifierStatut — doit lever PaymentException si aucune transaction trouvée")
    void verifierStatut_doitLeverExceptionSiIntrouvable() throws Exception {
        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.getTransactions(any(String[].class)))
                        .thenReturn(new Transaction[]{}))) {

            assertThatThrownBy(() -> provider.verifierStatut("inconnu"))
                    .isInstanceOf(PaymentException.class);
        }
    }

    // ---- libererFonds ----

    @Test
    @DisplayName("libererFonds — doit retourner la référence du virement en cas de succès")
    void libererFonds_doitRetournerReference() throws Exception {
        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.makeDeposit(anyMap()))
                        .thenReturn(buildTransactionResponse(true, "SUCCESS", "payout_789", "Virement effectué")))) {

            String reference = provider.libererFonds("237655547896", 5400.0, "order-1");

            assertThat(reference).isEqualTo("payout_789");
        }
    }

    @Test
    @DisplayName("libererFonds — doit lever PaymentException si le virement est refusé")
    void libererFonds_doitLeverExceptionSiRefuse() throws Exception {
        try (MockedConstruction<PaymentOperation> mocked = mockConstruction(PaymentOperation.class,
                (mock, context) -> when(mock.makeDeposit(anyMap()))
                        .thenReturn(buildTransactionResponse(true, "FAILED", null, "Numéro invalide")))) {

            assertThatThrownBy(() -> provider.libererFonds("237655547896", 5400.0, "order-1"))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Numéro invalide");
        }
    }

    // ---- getProviderName ----

    @Test
    @DisplayName("getProviderName — doit retourner MeSomb")
    void getProviderName_doitRetournerMesomb() {
        assertThat(provider.getProviderName()).isEqualTo("MeSomb");
    }
}
