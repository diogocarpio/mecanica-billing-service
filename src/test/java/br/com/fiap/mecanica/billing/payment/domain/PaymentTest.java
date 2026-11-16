package br.com.fiap.mecanica.billing.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void shouldCreatePendingPayment() {
        Payment payment = payment();

        assertThat(payment.getId()).isNotBlank();
        assertThat(payment.getQuoteId()).isEqualTo("QUOTE-1");
        assertThat(payment.getOsId()).isEqualTo("OS-1");
        assertThat(payment.getAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getCurrency()).isEqualTo("BRL");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getIdempotencyKey()).isNotBlank();
        assertThat(payment.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldAttachPreferenceAndApproveIdempotently() {
        Payment payment = payment();
        payment.attachPreference("PREF-1", "https://checkout.test/1", NOW.plusSeconds(1));

        assertThat(payment.applyProviderStatus(
                "MP-1",
                "approved",
                "accredited",
                PaymentStatus.APPROVED,
                NOW.plusSeconds(2)
        )).isTrue();
        assertThat(payment.applyProviderStatus(
                "MP-1",
                "pending",
                "pending_waiting_payment",
                PaymentStatus.PENDING,
                NOW.plusSeconds(3)
        )).isFalse();

        assertThat(payment.getPreferenceId()).isEqualTo("PREF-1");
        assertThat(payment.getCheckoutUrl()).isEqualTo("https://checkout.test/1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getExternalPaymentId()).isEqualTo("MP-1");
        assertThat(payment.getProviderStatusDetail()).isEqualTo("accredited");
        assertThat(payment.getApprovedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void shouldMarkCreationErrorAndAllowRetry() {
        Payment payment = payment();

        assertThat(payment.markError("PROVIDER", "Unavailable", NOW.plusSeconds(1)))
                .isTrue();
        assertThat(payment.canRetryPreference()).isTrue();

        payment.attachPreference("PREF-1", "https://checkout.test/1", NOW.plusSeconds(2));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getErrorCode()).isNull();
        assertThat(payment.getErrorMessage()).isNull();
    }

    @Test
    void shouldRejectInvalidCreation() {
        assertThatThrownBy(() -> Payment.create(
                "QUOTE-1",
                "OS-1",
                BigDecimal.ZERO,
                "BRL",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.create(
                "QUOTE-1",
                "OS-1",
                BigDecimal.ONE,
                "REAL",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Payment payment() {
        return Payment.create(
                "QUOTE-1",
                "OS-1",
                new BigDecimal("100.00"),
                "brl",
                NOW
        );
    }
}
