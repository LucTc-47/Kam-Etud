package com.kametude.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {

    private UUID transactionId;
    private UUID orderId;
    private String status;
    private String externalReference;
    private String message;
    private Double commission;
    private Double netAmount;
    private String payoutReference;
    private String ussdCode;
}
