package br.com.fiap.mecanica.billing.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;

public final class PaymentEventPayloads {

    private PaymentEventPayloads() {
    }

    public record StatusChanged(
            String paymentId,
            String quoteId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String externalPaymentId,
            String providerStatus,
            Instant changedAt
    ) {
    }

    public record Failed(
            String paymentId,
            String quoteId,
            PaymentStatus status,
            String code,
            String message,
            Instant failedAt
    ) {
    }
}
