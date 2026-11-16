package br.com.fiap.mecanica.billing.messaging.outbox;

import java.time.Clock;
import java.time.Instant;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.QuoteEventPayloads;
import br.com.fiap.mecanica.billing.quote.application.QuoteEventPublisher;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import org.springframework.stereotype.Component;

@Component
public class OutboxQuoteEventPublisher implements QuoteEventPublisher {

    private final OutboxService outboxService;
    private final Clock clock;

    public OutboxQuoteEventPublisher(OutboxService outboxService, Clock clock) {
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Override
    public void quoteCreated(Quote quote, EventContext context) {
        enqueue(
                EventTypes.QUOTE_CREATED,
                quote,
                new QuoteEventPayloads.Created(
                        quote.getId(),
                        quote.getCustomerId(),
                        quote.getTotal(),
                        quote.getCurrency(),
                        quote.getStatus(),
                        quote.getValidUntil()
                ),
                context
        );
    }

    @Override
    public void quoteSubmitted(Quote quote, EventContext context) {
        enqueue(
                EventTypes.QUOTE_SUBMITTED,
                quote,
                new QuoteEventPayloads.Submitted(
                        quote.getId(),
                        quote.getTotal(),
                        quote.getCurrency(),
                        quote.getStatus(),
                        quote.getValidUntil()
                ),
                context
        );
    }

    @Override
    public void quoteApproved(Quote quote, EventContext context) {
        enqueue(
                EventTypes.QUOTE_APPROVED,
                quote,
                new QuoteEventPayloads.Approved(
                        quote.getId(),
                        quote.getTotal(),
                        quote.getCurrency(),
                        quote.getStatus(),
                        quote.getDecidedAt()
                ),
                context
        );
    }

    @Override
    public void quoteRejected(Quote quote, EventContext context) {
        enqueue(
                EventTypes.QUOTE_REJECTED,
                quote,
                new QuoteEventPayloads.Rejected(
                        quote.getId(),
                        quote.getStatus(),
                        quote.getRejectionReason(),
                        quote.getDecidedAt()
                ),
                context
        );
    }

    @Override
    public void quoteCancelled(Quote quote, EventContext context) {
        enqueue(
                EventTypes.QUOTE_CANCELLED,
                quote,
                new QuoteEventPayloads.Cancelled(
                        quote.getId(),
                        quote.getStatus(),
                        quote.getRejectionReason(),
                        quote.getDecidedAt()
                ),
                context
        );
    }

    @Override
    public void quoteFailed(
            String osId,
            String code,
            String message,
            EventContext context
    ) {
        Instant now = Instant.now(clock);
        outboxService.enqueue(IntegrationEvent.billingEvent(
                EventTypes.QUOTE_FAILED,
                osId,
                new QuoteEventPayloads.Failed(code, message, now),
                context,
                now
        ));
    }

    private void enqueue(
            String eventType,
            Quote quote,
            Object payload,
            EventContext context
    ) {
        outboxService.enqueue(IntegrationEvent.billingEvent(
                eventType,
                quote.getOsId(),
                payload,
                context,
                Instant.now(clock)
        ));
    }
}
