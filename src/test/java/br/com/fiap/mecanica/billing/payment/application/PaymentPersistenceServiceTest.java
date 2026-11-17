package br.com.fiap.mecanica.billing.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.List;
import java.util.Optional;

import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import br.com.fiap.mecanica.billing.payment.infrastructure.PaymentRepository;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentEventPublisher eventPublisher;

    private PaymentPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new PaymentPersistenceService(
                repository,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreatePaymentOnce() {
        Quote quote = quote();
        when(repository.findByQuoteId("QUOTE-1")).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.createOrGet(quote);

        assertThat(payment.getQuoteId()).isEqualTo("QUOTE-1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(repository).save(payment);
    }

    @Test
    void shouldAttachPreferenceAndPublishPendingEvent() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(repository.save(payment)).thenReturn(payment);

        Payment result = service.attachPreference(
                payment.getId(),
                new MercadoPagoGateway.PreferenceResult(
                        "PREF-1",
                        "https://checkout.test/1"
                )
        );

        assertThat(result.getPreferenceId()).isEqualTo("PREF-1");
        verify(eventPublisher).paymentPending(
                org.mockito.ArgumentMatchers.eq(payment),
                any()
        );
    }

    @Test
    void shouldApproveVerifiedProviderPayment() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(repository.save(payment)).thenReturn(payment);

        Payment result = service.applyProviderPayment(new MercadoPagoGateway.ProviderPayment(
                "MP-1",
                payment.getId(),
                new BigDecimal("100.00"),
                "BRL",
                "approved",
                "accredited"
        ));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(eventPublisher).paymentApproved(
                org.mockito.ArgumentMatchers.eq(payment),
                any()
        );
    }

    @Test
    void shouldRejectMismatchedProviderAmount() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.applyProviderPayment(
                new MercadoPagoGateway.ProviderPayment(
                        "MP-1",
                        payment.getId(),
                        new BigDecimal("1.00"),
                        "BRL",
                        "approved",
                        "accredited"
                )
        )).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void shouldPublishFailureForUnknownStatus() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(repository.save(payment)).thenReturn(payment);

        service.applyProviderPayment(new MercadoPagoGateway.ProviderPayment(
                "MP-1",
                payment.getId(),
                new BigDecimal("100.00"),
                "BRL",
                "unexpected",
                "unknown"
        ));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ERROR);
        verify(eventPublisher).paymentFailed(
                org.mockito.ArgumentMatchers.eq(payment),
                any()
        );
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

    @Test
    void shouldReturnExistingPaymentForQuote() {
        Quote quote = quote();
        Payment existing = payment();
        when(repository.findByQuoteId("QUOTE-1")).thenReturn(Optional.of(existing));

        assertThat(service.createOrGet(quote)).isSameAs(existing);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRecoverPaymentAfterDuplicateKeyRace() {
        Quote quote = quote();
        Payment existing = payment();
        when(repository.findByQuoteId("QUOTE-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(repository.save(any(Payment.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThat(service.createOrGet(quote)).isSameAs(existing);
    }

    @Test
    void shouldRethrowDuplicateKeyWhenPaymentStillMissing() {
        Quote quote = quote();
        when(repository.findByQuoteId("QUOTE-1")).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.createOrGet(quote))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldReturnPaymentWhenPreferenceAlreadyAttached() {
        Payment payment = payment();
        payment.attachPreference("PREF-1", "https://checkout.test/1", NOW);
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        Payment result = service.attachPreference(
                payment.getId(),
                new MercadoPagoGateway.PreferenceResult("PREF-2", "https://checkout.test/2")
        );

        assertThat(result.getPreferenceId()).isEqualTo("PREF-1");
        verify(repository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldPublishWhenCreationFailureIsRecorded() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = service.markCreationFailed(payment.getId(), "provider down");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.ERROR);
        verify(eventPublisher).paymentFailed(any(), any());
    }

    @Test
    void shouldNotPublishWhenCreationFailureIsNoOp() {
        Payment payment = payment();
        payment.applyProviderStatus(
                "MP-1", "approved", "accredited", PaymentStatus.APPROVED, NOW
        );
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        Payment result = service.markCreationFailed(payment.getId(), "late failure");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(repository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRejectProviderPaymentWithoutExternalReference() {
        assertThatThrownBy(() -> service.applyProviderPayment(
                new MercadoPagoGateway.ProviderPayment(
                        "MP-1", " ", new BigDecimal("100.00"), "BRL", "approved", "accredited"
                )
        )).isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> service.applyProviderPayment(
                new MercadoPagoGateway.ProviderPayment(
                        "MP-1", null, new BigDecimal("100.00"), "BRL", "approved", "accredited"
                )
        )).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void shouldRejectProviderPaymentWithMismatchedAmountOrCurrency() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.applyProviderPayment(
                new MercadoPagoGateway.ProviderPayment(
                        "MP-1", payment.getId(), null, "BRL", "approved", "accredited"
                )
        )).isInstanceOf(InvalidPaymentStateException.class);
        assertThatThrownBy(() -> service.applyProviderPayment(
                new MercadoPagoGateway.ProviderPayment(
                        "MP-1", payment.getId(), new BigDecimal("100.00"), "USD", "approved", "accredited"
                )
        )).isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void shouldApplyNonTerminalProviderStatusesWithoutPublishing() {
        for (String status : List.of("pending", "in_process", "in_mediation")) {
            Payment payment = payment();
            when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
            when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.applyProviderPayment(new MercadoPagoGateway.ProviderPayment(
                    "MP-1", payment.getId(), new BigDecimal("100.00"), "BRL", status, "detail"
            ));

            assertThat(payment.getStatus()).isNotEqualTo(PaymentStatus.ERROR);
        }

        verify(eventPublisher, never()).paymentApproved(any(), any());
        verify(eventPublisher, never()).paymentRejected(any(), any());
        verify(eventPublisher, never()).paymentFailed(any(), any());
    }

    @Test
    void shouldPublishRejectionForRejectedAndCancelledStatuses() {
        for (String status : List.of("rejected", "cancelled")) {
            Payment payment = payment();
            when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
            when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.applyProviderPayment(new MercadoPagoGateway.ProviderPayment(
                    "MP-1", payment.getId(), new BigDecimal("100.00"), "BRL", status, "detail"
            ));

            assertThat(payment.getStatus()).isIn(PaymentStatus.REJECTED, PaymentStatus.CANCELLED);
        }

        verify(eventPublisher, times(2)).paymentRejected(any(), any());
    }

    @Test
    void shouldApplyNullProviderStatusAsError() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.applyProviderPayment(new MercadoPagoGateway.ProviderPayment(
                "MP-1", payment.getId(), new BigDecimal("100.00"), "BRL", null, null
        ));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ERROR);
        verify(eventPublisher).paymentFailed(any(), any());
    }

    @Test
    void shouldReturnUnchangedPaymentWhenProviderStatusAlreadyApplied() {
        Payment payment = payment();
        when(repository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.applyProviderPayment(new MercadoPagoGateway.ProviderPayment(
                "MP-1", payment.getId(), new BigDecimal("100.00"), "BRL", "approved", "accredited"
        ));
        Payment second = service.applyProviderPayment(new MercadoPagoGateway.ProviderPayment(
                "MP-2", payment.getId(), new BigDecimal("100.00"), "BRL", "approved", "accredited"
        ));

        assertThat(second.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(repository, times(1)).save(any());
    }

    @Test
    void shouldThrowNotFoundForMissingPayment() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    private Quote quote() {
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
                QuoteStatus.APPROVED,
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
