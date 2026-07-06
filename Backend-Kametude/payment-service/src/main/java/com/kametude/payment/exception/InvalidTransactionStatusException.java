package com.kametude.payment.exception;

public class InvalidTransactionStatusException extends PaymentException {
    public InvalidTransactionStatusException(String currentStatus) {
        super("INVALID_TRANSACTION_STATUS",
              "Cette transaction ne peut pas être libérée (statut actuel : " + currentStatus + ")");
    }
}