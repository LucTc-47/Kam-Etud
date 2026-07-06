package com.kametude.payment.provider;

import com.kametude.payment.dto.PaymentRequestDTO;
import com.kametude.payment.dto.PaymentResponseDTO;

public interface PaymentProvider {

    /**
     * Initie une transaction de paiement auprès du fournisseur (MeSomb...).
     */
    PaymentResponseDTO initierPaiement(PaymentRequestDTO requestDTO);

    /**
     * Vérifie le statut d'une transaction auprès du fournisseur, via sa référence externe.
     */
    String verifierStatut(String externalReference);

     /**
     * Libère les fonds vers le prestataire (Payout côté MeSomb).
     */
    String libererFonds(String sellerPhone, Double amount, String orderReference);

    /**
     * Permet de savoir quel fournisseur est utilisé par cette implémentation (ex: "MeSomb" ou un autre plus tard..).
     */
    String getProviderName();
}