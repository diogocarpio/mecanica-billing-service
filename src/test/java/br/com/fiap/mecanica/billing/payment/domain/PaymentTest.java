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

    @Test
    void shouldExposeAllStateViaGetters() {
        Payment payment = payment();
        payment.attachPreference("PREF-1", "https://checkout.test/1", NOW.plusSeconds(1));
        payment.applyProviderStatus(
                "MP-1", "approved", "accredited", PaymentStatus.APPROVED, NOW.plusSeconds(2)
        );

        assertThat(payment.getId()).isNotBlank();
        assertThat(payment.getQuoteId()).isEqualTo("QUOTE-1");
        assertThat(payment.getOsId()).isEqualTo("OS-1");
        assertThat(payment.getAmount()).isEqualByComparingTo("100.00");
        assertThat(payment.getCurrency()).isEqualTo("BRL");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getIdempotencyKey()).isNotBlank();
        assertThat(payment.getPreferenceId()).isEqualTo("PREF-1");
        assertThat(payment.getCheckoutUrl()).isEqualTo("https://checkout.test/1");
        assertThat(payment.getExternalPaymentId()).isEqualTo("MP-1");
        assertThat(payment.getProviderStatus()).isEqualTo("approved");
        assertThat(payment.getProviderStatusDetail()).isEqualTo("accredited");
        assertThat(payment.getErrorCode()).isNull();
        assertThat(payment.getErrorMessage()).isNull();
        assertThat(payment.getCreatedAt()).isEqualTo(NOW);
        assertThat(payment.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(payment.getApprovedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(payment.getDocumentVersion()).isNull();
    }

    @Test
    void shouldTreatAllTerminalStatusesAsFinal() {
        Payment approved = payment();
        approved.applyProviderStatus(
                "MP-1", "approved", "x", PaymentStatus.APPROVED, NOW.plusSeconds(1)
        );
        assertThat(approved.applyProviderStatus(
                "MP-2", "pending", "x", PaymentStatus.PENDING, NOW.plusSeconds(2)
        )).isFalse();
        assertThat(approved.markError("X", "y", NOW.plusSeconds(3))).isFalse();

        Payment rejected = payment();
        rejected.applyProviderStatus(
                "MP-1", "rejected", "x", PaymentStatus.REJECTED, NOW.plusSeconds(1)
        );
        assertThat(rejected.applyProviderStatus(
                "MP-2", "pending", "x", PaymentStatus.PENDING, NOW.plusSeconds(2)
        )).isFalse();
        assertThat(rejected.markError("X", "y", NOW.plusSeconds(3))).isFalse();

        Payment cancelled = payment();
        cancelled.applyProviderStatus(
                "MP-1", "cancelled", "x", PaymentStatus.CANCELLED, NOW.plusSeconds(1)
        );
        assertThat(cancelled.applyProviderStatus(
                "MP-2", "pending", "x", PaymentStatus.PENDING, NOW.plusSeconds(2)
        )).isFalse();
        assertThat(cancelled.markError("X", "y", NOW.plusSeconds(3))).isFalse();
    }

    @Test
    void shouldNotDuplicateIdenticalError() {
        Payment payment = payment();
        payment.markError("PROVIDER", "Unavailable", NOW.plusSeconds(1));

        assertThat(payment.markError("PROVIDER", "Unavailable", NOW.plusSeconds(2)))
                .isFalse();
        assertThat(payment.markError("PROVIDER", "Other", NOW.plusSeconds(3)))
                .isTrue();
    }

    @Test
    void shouldUpdateErrorWhenCodeDiffers() {
        Payment payment = payment();
        payment.markError("PROVIDER", "Unavailable", NOW.plusSeconds(1));

        assertThat(payment.markError("OTHER", "Unavailable", NOW.plusSeconds(2)))
                .isTrue();
        assertThat(payment.getErrorCode()).isEqualTo("OTHER");
    }

    @Test
    void shouldNotRetryWhenPreferenceAlreadyExists() {
        Payment payment = payment();
        payment.attachPreference("PREF-1", "https://checkout.test/1", NOW.plusSeconds(1));
        payment.markError("PROVIDER", "Unavailable", NOW.plusSeconds(2));

        assertThat(payment.canRetryPreference()).isFalse();
    }

    @Test
    void shouldRejectInvalidMutations() {
        Payment payment = payment();

        assertThatThrownBy(() -> payment.applyProviderStatus(
                "MP-1", "approved", "x", null, NOW
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> payment.attachPreference(" ", "url", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.attachPreference("pref", " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.attachPreference("pref", "url", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> payment.applyProviderStatus(
                " ", "approved", "x", PaymentStatus.APPROVED, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.applyProviderStatus(
                "MP-1", " ", "x", PaymentStatus.APPROVED, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.markError(" ", "msg", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.markError("CODE", " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidCreationArguments() {
        assertThatThrownBy(() -> Payment.create(
                null, "OS-1", BigDecimal.ONE, "BRL", NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.create(
                "QUOTE-1", " ", BigDecimal.ONE, "BRL", NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.create(
                "QUOTE-1", "OS-1", null, "BRL", NOW
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Payment.create(
                "QUOTE-1", "OS-1", BigDecimal.ONE, " ", NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.create(
                "QUOTE-1", "OS-1", BigDecimal.ONE, "BRL", null
        )).isInstanceOf(NullPointerException.class);
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
