package com.kametude.payment.exception;

import java.util.UUID;

public class TransactionNotFoundException extends PaymentException {
    public TransactionNotFoundException(UUID transactionId) {
        super("TRANSACTION_NOT_FOUND", "Transaction introuvable : " + transactionId);
    }
}