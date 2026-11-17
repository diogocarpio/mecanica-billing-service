package br.com.fiap.mecanica.billing.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.infrastructure.PaymentRepository;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import br.com.fiap.mecanica.billing.quote.infrastructure.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentPersistenceService persistenceService;
    @Mock
    private MercadoPagoGateway gateway;
    @Mock
    private MercadoPagoWebhookSignatureValidator signatureValidator;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                quoteRepository,
                paymentRepository,
                persistenceService,
                gateway,
                signatureValidator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5)
        );
    }

    @Test
    void shouldCreatePreferenceForApprovedQuote() {
        Quote quote = quote(QuoteStatus.APPROVED);
        Payment payment = payment();
        var preference = new MercadoPagoGateway.PreferenceResult(
                "PREF-1",
                "https://checkout.test/1"
        );
        when(quoteRepository.findById("QUOTE-1")).thenReturn(Optional.of(quote));
        when(persistenceService.createOrGet(quote)).thenReturn(payment);
        when(gateway.createPreference(any())).thenReturn(preference);
        when(persistenceService.attachPreference(payment.getId(), preference))
                .thenReturn(payment);

        assertThat(service.createPreference("QUOTE-1", "payer@testuser.com"))
                .isSameAs(payment);

        verify(gateway).createPreference(any());
        verify(persistenceService).attachPreference(payment.getId(), preference);
    }

    @Test
    void shouldRejectPaymentForNonApprovedQuote() {
        when(quoteRepository.findById("QUOTE-1"))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_APPROVAL)));

        assertThatThrownBy(() -> service.createPreference(
                "QUOTE-1",
                "payer@testuser.com"
        )).isInstanceOf(InvalidPaymentStateException.class);

        verify(gateway, never()).createPreference(any());
    }

    @Test
    void shouldReuseExistingPreference() {
        Quote quote = quote(QuoteStatus.APPROVED);
        Payment payment = payment();
        payment.attachPreference("PREF-1", "https://checkout.test/1", NOW);
        when(quoteRepository.findById("QUOTE-1")).thenReturn(Optional.of(quote));
        when(persistenceService.createOrGet(quote)).thenReturn(payment);

        assertThat(service.createPreference("QUOTE-1", "payer@testuser.com"))
                .isSameAs(payment);

        verify(gateway, never()).createPreference(any());
    }

    @Test
    void shouldPersistProviderFailure() {
        Quote quote = quote(QuoteStatus.APPROVED);
        Payment payment = payment();
        when(quoteRepository.findById("QUOTE-1")).thenReturn(Optional.of(quote));
        when(persistenceService.createOrGet(quote)).thenReturn(payment);
        when(gateway.createPreference(any()))
                .thenThrow(new MercadoPagoException("unavailable"));

        assertThatThrownBy(() -> service.createPreference(
                "QUOTE-1",
                "payer@testuser.com"
        )).isInstanceOf(MercadoPagoException.class);

        verify(persistenceService).markCreationFailed(payment.getId(), "unavailable");
    }

    @Test
    void shouldValidateWebhookAndQueryProvider() {
        var providerPayment = providerPayment();
        when(gateway.getPayment("MP-1")).thenReturn(providerPayment);

        assertThat(service.handleWebhook(
                "MP-1",
                "payment",
                "request-1",
                "signature"
        )).isTrue();

        verify(signatureValidator).validate("MP-1", "request-1", "signature");
        verify(persistenceService).applyProviderPayment(providerPayment);
        assertThat(service.handleWebhook("MP-1", "merchant_order", "x", "x"))
                .isFalse();
    }

    @Test
    void shouldReconcileOldPendingPayments() {
        Payment payment = payment();
        var providerPayment = providerPayment();
        when(paymentRepository
                .findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        any(),
                        eq(NOW.minus(Duration.ofMinutes(5)))
                )).thenReturn(List.of(payment));
        when(gateway.findPaymentByExternalReference(payment.getId()))
                .thenReturn(Optional.of(providerPayment));

        service.reconcilePendingPayments();

        verify(persistenceService).applyProviderPayment(providerPayment);
    }

    @Test
    void shouldThrowNotFoundForMissingPaymentLookups() {
        when(paymentRepository.findById("missing")).thenReturn(Optional.empty());
        when(paymentRepository.findByQuoteId("missing")).thenReturn(Optional.empty());
        when(paymentRepository.findByOsId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> service.findByQuoteId("missing"))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> service.findByOsId("missing"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void shouldRejectPreferenceForMissingQuote() {
        when(quoteRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPreference("missing", "payer@test.com"))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void shouldReturnTerminalPaymentWithoutCreatingPreference() {
        Quote quote = quote(QuoteStatus.APPROVED);
        Payment payment = payment();
        payment.applyProviderStatus(
                "MP-1", "approved", "x", br.com.fiap.mecanica.billing.payment.domain.PaymentStatus.APPROVED, NOW
        );
        when(quoteRepository.findById("QUOTE-1")).thenReturn(Optional.of(quote));
        when(persistenceService.createOrGet(quote)).thenReturn(payment);

        assertThat(service.createPreference("QUOTE-1", "payer@test.com")).isSameAs(payment);

        verify(gateway, never()).createPreference(any());
    }

    @Test
    void shouldRetryPreferenceCreationAfterError() {
        Quote quote = quote(QuoteStatus.APPROVED);
        Payment payment = payment();
        payment.markError("PROVIDER", "down", NOW);
        var preference = new MercadoPagoGateway.PreferenceResult("PREF-1", "https://checkout.test/1");
        when(quoteRepository.findById("QUOTE-1")).thenReturn(Optional.of(quote));
        when(persistenceService.createOrGet(quote)).thenReturn(payment);
        when(gateway.createPreference(any())).thenReturn(preference);
        when(persistenceService.attachPreference(payment.getId(), preference)).thenReturn(payment);

        assertThat(service.createPreference("QUOTE-1", "payer@test.com")).isSameAs(payment);

        verify(gateway).createPreference(any());
    }

    @Test
    void shouldSkipReconciliationWhenProviderHasNoPayment() {
        Payment payment = payment();
        when(paymentRepository
                .findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        any(),
                        eq(NOW.minus(Duration.ofMinutes(5)))
                )).thenReturn(List.of(payment));
        when(gateway.findPaymentByExternalReference(payment.getId()))
                .thenReturn(Optional.empty());

        service.reconcilePendingPayments();

        verify(persistenceService, never()).applyProviderPayment(any());
    }

    private Payment payment() {
        return Payment.create(
                "QUOTE-1",
                "OS-1",
                new BigDecimal("100.00"),
                "BRL",
                NOW
        );
    }

    private MercadoPagoGateway.ProviderPayment providerPayment() {
        return new MercadoPagoGateway.ProviderPayment(
                "MP-1",
                payment().getId(),
                new BigDecimal("100.00"),
                "BRL",
                "approved",
                "accredited"
        );
    }

    private Quote quote(QuoteStatus status) {
        return new Quote(
                "QUOTE-1",
                "OS-1",
                "CUSTOMER-1",
                List.of(new QuoteItem(
                        "Labor",
                        QuoteItemType.LABOR,
                        BigDecimal.ONE,
                        new BigDecimal("100.00")
                )),
                new BigDecimal("100.00"),
                "BRL",
                status,
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60),
                NOW,
                NOW.minusSeconds(30),
                NOW.minusSeconds(10),
                null,
                1L
        );
    }
}
