package br.com.fiap.mecanica.billing.messaging.outbox;

import java.time.Clock;
import java.time.Instant;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.PaymentEventPayloads;
import br.com.fiap.mecanica.billing.payment.application.PaymentEventPublisher;
import br.com.fiap.mecanica.billing.payment.domain.Payment;
import org.springframework.stereotype.Component;

@Component
public class OutboxPaymentEventPublisher implements PaymentEventPublisher {

    private final OutboxService outboxService;
    private final Clock clock;

    public OutboxPaymentEventPublisher(OutboxService outboxService, Clock clock) {
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Override
    public void paymentPending(Payment payment, EventContext context) {
        statusEvent(EventTypes.PAYMENT_PENDING, payment, context);
    }

    @Override
    public void paymentApproved(Payment payment, EventContext context) {
        statusEvent(EventTypes.PAYMENT_APPROVED, payment, context);
    }

    @Override
    public void paymentRejected(Payment payment, EventContext context) {
        statusEvent(EventTypes.PAYMENT_REJECTED, payment, context);
    }

    @Override
    public void paymentFailed(Payment payment, EventContext context) {
        Instant now = Instant.now(clock);
        enqueue(
                EventTypes.PAYMENT_FAILED,
                payment,
                new PaymentEventPayloads.Failed(
                        payment.getId(),
                        payment.getQuoteId(),
                        payment.getStatus(),
                        payment.getErrorCode(),
                        payment.getErrorMessage(),
                        now
                ),
                context,
                now
        );
    }

    private void statusEvent(String eventType, Payment payment, EventContext context) {
        Instant now = Instant.now(clock);
        enqueue(
                eventType,
                payment,
                new PaymentEventPayloads.StatusChanged(
                        payment.getId(),
                        payment.getQuoteId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getStatus(),
                        payment.getExternalPaymentId(),
                        payment.getProviderStatus(),
                        now
                ),
                context,
                now
        );
    }

    private void enqueue(
            String eventType,
            Payment payment,
            Object payload,
            EventContext context,
            Instant now
    ) {
        outboxService.enqueue(IntegrationEvent.billingEvent(
                eventType,
                payment.getOsId(),
                payload,
                context,
                now
        ));
    }
}
