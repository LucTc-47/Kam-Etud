package com.kametude.payment.integration;

import com.kametude.payment.client.*;
import com.kametude.payment.dto.PaymentResponseDTO;
import com.kametude.payment.provider.PaymentProvider;
import com.kametude.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired PaymentTransactionRepository repository;
    @MockitoBean PaymentProvider paymentProvider;
    @MockitoBean BusinessClient businessClient;
    @MockitoBean IdentityClient identityClient;
    @MockitoBean SupportClient supportClient;

    UUID orderId;
    UUID clientId;
    UUID studentId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        reset(paymentProvider, businessClient, identityClient, supportClient);
        orderId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        when(businessClient.getOrder(orderId)).thenReturn(new BusinessOrderContext(orderId, clientId, studentId, 6000.0, "pending"));
        when(identityClient.getPayoutProfile(studentId)).thenReturn(new IdentityProfile("237655000000"));
        when(paymentProvider.getProviderName()).thenReturn("MeSomb");
        when(paymentProvider.initierPaiement(any())).thenReturn(PaymentResponseDTO.builder()
                .status("HELD").externalReference("collect-1").message("Confirmez sur le telephone").build());
        when(paymentProvider.verifierStatut(any())).thenReturn("SUCCESS");
        when(paymentProvider.libererFonds(any(), any(), any())).thenReturn("payout-1");
    }

    @Test
    void clientInitiatesIdempotentPaymentFromOrderData() throws Exception {
        String body = "{\"orderId\":\"%s\",\"phone\":\"+237 670 000 000\",\"amount\":1}".formatted(orderId);
        initiate(body).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.commission").value(600.0));
        initiate(body).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HELD"));
        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void anotherClientCannotPayTheOrder() throws Exception {
        mockMvc.perform(post("/api/payments/initiate")
                        .header("X-User-Id", UUID.randomUUID()).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"phone\":\"237670000000\"}".formatted(orderId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void completedOrderCanReleaseHeldFunds() throws Exception {
        initiate("{\"orderId\":\"%s\",\"phone\":\"237670000000\"}".formatted(orderId)).andExpect(status().isOk());
        when(businessClient.getOrder(orderId)).thenReturn(new BusinessOrderContext(orderId, clientId, studentId, 6000.0, "completed"));

        mockMvc.perform(post("/api/payments/order/{id}/release", orderId)
                        .header("X-User-Id", clientId).header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.payoutReference").value("payout-1"));

        mockMvc.perform(get("/api/payments/order/{id}", orderId)
                        .header("X-User-Id", studentId).header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
    }

    private org.springframework.test.web.servlet.ResultActions initiate(String body) throws Exception {
        return mockMvc.perform(post("/api/payments/initiate")
                .header("X-User-Id", clientId).header("X-User-Role", "CLIENT")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
