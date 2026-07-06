package com.kametude.payment.service;

import com.kametude.payment.entity.PaymentTransaction;
import com.kametude.payment.exception.InvalidTransactionStatusException;
import com.kametude.payment.exception.TransactionNotFoundException;
import com.kametude.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransactionManager {
    private final PaymentTransactionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentReservation reserveCollection(UUID orderId, BigDecimal amount, BigDecimal commission,
                                                String payerPhone, String sellerPhone, String provider) {
        PaymentTransaction existing = repository.findLockedByOrderId(orderId).orElse(null);
        if (existing != null) {
            if (existing.getCollectionKey() == null) {
                existing.setCollectionKey("collect-" + orderId);
            }
            if (existing.getCollectionAttempt() == null) {
                existing.setCollectionAttempt(1);
            }
            if (existing.getAmount().compareTo(amount) != 0) {
                throw new InvalidTransactionStatusException("AMOUNT_CHANGED");
            }
            if ("FAILED".equals(existing.getStatus())) {
                existing.setStatus("INITIATING");
                existing.setCollectionAttempt(existing.getCollectionAttempt() + 1);
                existing.setExternalReference(null);
                existing.setLastError(null);
                return new PaymentReservation(repository.save(existing), true);
            }
            return new PaymentReservation(repository.save(existing), false);
        }

        PaymentTransaction created = PaymentTransaction.builder()
                .orderId(orderId)
                .amount(amount)
                .commission(commission)
                .phone(payerPhone)
                .sellerPhone(sellerPhone)
                .provider(provider)
                .status("INITIATING")
                .collectionKey("collect-" + orderId)
                .collectionAttempt(1)
                .build();
        return new PaymentReservation(repository.saveAndFlush(created), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction recordCollectionResult(UUID transactionId, String status,
                                                     String externalReference, String error) {
        PaymentTransaction transaction = findLocked(transactionId);
        transaction.setStatus(status);
        transaction.setExternalReference(externalReference);
        transaction.setLastError(error);
        return repository.save(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentReservation reservePayout(UUID orderId) {
        PaymentTransaction transaction = repository.findLockedByOrderId(orderId)
                .orElseThrow(() -> new TransactionNotFoundException(orderId));
        if ("RELEASED".equals(transaction.getStatus())
                || "PAYOUT_IN_PROGRESS".equals(transaction.getStatus())
                || "PAYOUT_UNKNOWN".equals(transaction.getStatus())) {
            return new PaymentReservation(transaction, false);
        }
        if (!"HELD".equals(transaction.getStatus())) {
            throw new InvalidTransactionStatusException(transaction.getStatus());
        }
        transaction.setStatus("PAYOUT_IN_PROGRESS");
        transaction.setPayoutKey("payout-" + orderId);
        transaction.setLastError(null);
        return new PaymentReservation(repository.save(transaction), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction recordPayoutSuccess(UUID transactionId, String payoutReference) {
        PaymentTransaction transaction = findLocked(transactionId);
        transaction.setStatus("RELEASED");
        transaction.setPayoutReference(payoutReference);
        transaction.setLastError(null);
        return repository.save(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction markUnknown(UUID transactionId, String status, String error) {
        PaymentTransaction transaction = findLocked(transactionId);
        transaction.setStatus(status);
        transaction.setLastError(truncate(error));
        return repository.save(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction updateVerifiedStatus(UUID transactionId, String status) {
        PaymentTransaction transaction = findLocked(transactionId);
        if (!"RELEASED".equals(transaction.getStatus())) {
            transaction.setStatus(status);
        }
        return repository.save(transaction);
    }

    @Transactional(readOnly = true)
    public PaymentTransaction findByOrder(UUID orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> new TransactionNotFoundException(orderId));
    }

    private PaymentTransaction findLocked(UUID transactionId) {
        return repository.findLockedById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
