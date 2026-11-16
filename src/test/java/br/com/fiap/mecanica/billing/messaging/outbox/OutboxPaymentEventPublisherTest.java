package br.com.fiap.mecanica.billing.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPaymentEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private OutboxService outboxService;

    private OutboxPaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPaymentEventPublisher(
                outboxService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreatePaymentEventEnvelopes() {
        Payment payment = Payment.create(
                "QUOTE-1",
                "OS-1",
                new BigDecimal("100.00"),
                "BRL",
                NOW
        );
        EventContext context = EventContext.started("OS-1");

        publisher.paymentPending(payment, context);
        payment.applyProviderStatus(
                "MP-1",
                "approved",
                "accredited",
                PaymentStatus.APPROVED,
                NOW
        );
        publisher.paymentApproved(payment, context);
        publisher.paymentRejected(payment, context);
        publisher.paymentFailed(payment, context);

        ArgumentCaptor<IntegrationEvent<?>> captor =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outboxService, times(4)).enqueue(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(IntegrationEvent::eventType)
                .containsExactly(
                        EventTypes.PAYMENT_PENDING,
                        EventTypes.PAYMENT_APPROVED,
                        EventTypes.PAYMENT_REJECTED,
                        EventTypes.PAYMENT_FAILED
                );
    }
}
