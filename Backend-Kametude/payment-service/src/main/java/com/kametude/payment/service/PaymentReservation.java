package com.kametude.payment.service;

import com.kametude.payment.entity.PaymentTransaction;

public record PaymentReservation(PaymentTransaction transaction, boolean executeProviderCall) {
}
