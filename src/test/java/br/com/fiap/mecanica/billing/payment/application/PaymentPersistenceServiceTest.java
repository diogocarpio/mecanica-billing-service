package br.com.fiap.mecanica.billing.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
