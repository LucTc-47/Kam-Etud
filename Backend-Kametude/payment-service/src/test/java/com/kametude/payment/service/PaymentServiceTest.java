package com.kametude.payment.service;

import com.kametude.payment.client.*;
import com.kametude.payment.dto.PaymentRequestDTO;
import com.kametude.payment.dto.PaymentResponseDTO;
import com.kametude.payment.entity.PaymentTransaction;
import com.kametude.payment.exception.InvalidTransactionStatusException;
import com.kametude.payment.provider.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PaymentProvider paymentProvider;
    @Mock PaymentTransactionManager transactionManager;
    @Mock BusinessClient businessClient;
    @Mock IdentityClient identityClient;
    @Mock SupportClient supportClient;
    @InjectMocks PaymentService service;

    UUID orderId;
    UUID clientId;
    UUID studentId;
    PaymentRequestDTO request;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        request = PaymentRequestDTO.builder().orderId(orderId).phone("+237 670 000 000")
                // Anciennes valeurs falsifiees : elles doivent etre ignorees.
                .amount(1.0).sellerPhone("237600000000").build();
    }

    @Test
    void initiateUsesTrustedOrderAmountAndSellerProfile() {
        when(businessClient.getOrder(orderId)).thenReturn(order("pending"));
        when(identityClient.getPayoutProfile(studentId)).thenReturn(new IdentityProfile("237655000000"));
        PaymentTransaction initiating = transaction("INITIATING");
        initiating.setCollectionKey("collect-" + orderId);
        when(transactionManager.reserveCollection(eq(orderId), any(), any(), any(), any(), any()))
                .thenReturn(new PaymentReservation(initiating, true));
        when(paymentProvider.initierPaiement(any())).thenReturn(PaymentResponseDTO.builder()
                .status("HELD").externalReference("collect-1").message("ok").build());
        when(paymentProvider.getProviderName()).thenReturn("MeSomb");
        when(transactionManager.recordCollectionResult(eq(initiating.getId()), eq("HELD"), eq("collect-1"), isNull()))
                .thenAnswer(invocation -> {
                    initiating.setStatus("HELD");
                    initiating.setExternalReference("collect-1");
                    return initiating;
                });

        PaymentResponseDTO response = service.initiate(clientId, "CLIENT", request);

        assertThat(response.getStatus()).isEqualTo("HELD");
        assertThat(response.getCommission()).isEqualTo(600.0);
        assertThat(response.getNetAmount()).isEqualTo(5400.0);
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentRequestDTO.class);
        verify(paymentProvider).initierPaiement(captor.capture());
        verify(businessClient).markPaymentHeld(orderId);
        assertThat(captor.getValue().getAmount()).isEqualTo(6000.0);
        assertThat(captor.getValue().getSellerPhone()).isEqualTo("237655000000");
    }

    @Test
    void initiateIsIdempotentForHeldTransaction() {
        PaymentTransaction existing = transaction("HELD");
        when(businessClient.getOrder(orderId)).thenReturn(order("pending"));
        when(identityClient.getPayoutProfile(studentId)).thenReturn(new IdentityProfile("237655000000"));
        when(paymentProvider.getProviderName()).thenReturn("MeSomb");
        when(transactionManager.reserveCollection(eq(orderId), any(), any(), any(), any(), any()))
                .thenReturn(new PaymentReservation(existing, false));

        PaymentResponseDTO response = service.initiate(clientId, "CLIENT", request);

        assertThat(response.getTransactionId()).isEqualTo(existing.getId());
        verify(paymentProvider, never()).initierPaiement(any());
        verify(paymentProvider, never()).libererFonds(any(), any(), any());
        verify(businessClient).markPaymentHeld(orderId);
    }

    @Test
    void failedCollectionIsNeverStoredAsHeld() {
        when(businessClient.getOrder(orderId)).thenReturn(order("pending"));
        when(identityClient.getPayoutProfile(studentId)).thenReturn(new IdentityProfile("237655000000"));
        PaymentTransaction initiating = transaction("INITIATING");
        initiating.setCollectionKey("collect-" + orderId);
        when(transactionManager.reserveCollection(eq(orderId), any(), any(), any(), any(), any()))
                .thenReturn(new PaymentReservation(initiating, true));
        when(paymentProvider.initierPaiement(any())).thenReturn(PaymentResponseDTO.builder().status("FAILED").message("refuse").build());
        when(paymentProvider.getProviderName()).thenReturn("MeSomb");
        when(transactionManager.recordCollectionResult(eq(initiating.getId()), eq("FAILED"), isNull(), eq("refuse")))
                .thenAnswer(invocation -> {
                    initiating.setStatus("FAILED");
                    return initiating;
                });

        PaymentResponseDTO response = service.initiate(clientId, "CLIENT", request);

        assertThat(response.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void releaseRequiresCompletedOrderAndHeldFunds() {
        PaymentTransaction held = transaction("HELD");
        when(businessClient.getOrder(orderId)).thenReturn(order("completed"));
        when(transactionManager.reservePayout(orderId)).thenReturn(new PaymentReservation(held, true));
        when(paymentProvider.libererFonds(any(), any(), any())).thenReturn("payout-1");
        when(transactionManager.recordPayoutSuccess(held.getId(), "payout-1")).thenAnswer(invocation -> {
            held.setStatus("RELEASED");
            held.setPayoutReference("payout-1");
            return held;
        });

        PaymentResponseDTO response = service.releaseByOrder(orderId, clientId, "CLIENT");

        assertThat(response.getStatus()).isEqualTo("RELEASED");
        assertThat(response.getPayoutReference()).isEqualTo("payout-1");
    }

    @Test
    void releaseRejectsNonCompletedOrder() {
        when(businessClient.getOrder(orderId)).thenReturn(order("delivered"));
        assertThatThrownBy(() -> service.releaseByOrder(orderId, clientId, "CLIENT"))
                .isInstanceOf(InvalidTransactionStatusException.class);
    }

    @Test
    void initiateRejectsStudentWithoutPayoutPhoneBeforeCallingProvider() {
        when(businessClient.getOrder(orderId)).thenReturn(order("pending"));
        when(identityClient.getPayoutProfile(studentId)).thenReturn(new IdentityProfile(null));

        assertThatThrownBy(() -> service.initiate(clientId, "CLIENT", request))
                .isInstanceOf(com.kametude.payment.exception.PaymentException.class)
                .hasMessageContaining("L'etudiant doit configurer");

        verifyNoInteractions(paymentProvider, transactionManager);
    }

    private BusinessOrderContext order(String status) {
        return new BusinessOrderContext(orderId, clientId, studentId, 6000.0, status);
    }

    private PaymentTransaction transaction(String status) {
        return PaymentTransaction.builder().id(UUID.randomUUID()).orderId(orderId)
                .amount(new BigDecimal("6000.00")).commission(new BigDecimal("600.00"))
                .status(status).provider("MeSomb").sellerPhone("237655000000")
                .collectionKey("collect-" + orderId).payoutKey("payout-" + orderId).build();
    }
}
