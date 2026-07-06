package com.kametude.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestDTO {

    @NotNull
    private UUID orderId;
    @NotBlank
    private String phone;          // numéro du client (payeur)

    /* Ancien contrat : montant et numero vendeur fournis par React.
       Ils restent lisibles mais sont ignores au profit de Business/Identity. */
    private Double amount;
    private String sellerPhone;    // numéro du prestataire (destinataire à la libération escrow)
    // Cle technique definie par Payment Service et transmise comme X-MeSomb-TrxID.
    private String idempotencyKey;
}
