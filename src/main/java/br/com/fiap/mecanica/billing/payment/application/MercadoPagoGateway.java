package br.com.fiap.mecanica.billing.payment.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface MercadoPagoGateway {

    PreferenceResult createPreference(PreferenceRequest request);

    ProviderPayment getPayment(String externalPaymentId);

    Optional<ProviderPayment> findPaymentByExternalReference(String paymentId);

    record PreferenceRequest(
            String paymentId,
            String quoteId,
            String osId,
            BigDecimal amount,
            String currency,
            String payerEmail,
            String idempotencyKey
    ) {
    }

    record PreferenceResult(String preferenceId, String checkoutUrl) {
    }

    record ProviderPayment(
            String externalPaymentId,
            String externalReference,
            BigDecimal amount,
            String currency,
            String status,
            String statusDetail
    ) {
    }
}
