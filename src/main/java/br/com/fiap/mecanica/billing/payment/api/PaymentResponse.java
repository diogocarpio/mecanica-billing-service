package br.com.fiap.mecanica.billing.payment.api;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;

public record PaymentResponse(
        String id,
        String quoteId,
        String osId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String preferenceId,
        String checkoutUrl,
        String externalPaymentId,
        String providerStatus,
        String providerStatusDetail,
        String errorCode,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt
) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getQuoteId(),
                payment.getOsId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPreferenceId(),
                payment.getCheckoutUrl(),
                payment.getExternalPaymentId(),
                payment.getProviderStatus(),
                payment.getProviderStatusDetail(),
                payment.getErrorCode(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getApprovedAt()
        );
    }
}
