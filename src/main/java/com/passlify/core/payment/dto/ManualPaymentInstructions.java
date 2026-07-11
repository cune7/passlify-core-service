package com.passlify.core.payment.dto;

/**
 * Offline payment instructions shown to a buyer for a MANUAL-provider order: where to
 * send the bank transfer, how much, and the reference to quote so the organizer can
 * reconcile it.
 */
public record ManualPaymentInstructions(
        String accountHolder,
        String accountNumber,
        long amountMinor,
        String currency,
        String reference) {
}
