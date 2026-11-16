package br.com.fiap.mecanica.billing.messaging.listener;

import java.io.IOException;

import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.OsEventPayloads;
import br.com.fiap.mecanica.billing.shared.observability.BillingMetrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class QuoteEventListener {

    private static final TypeReference<IntegrationEvent<OsEventPayloads.QuoteRequested>>
            QUOTE_REQUESTED_TYPE = new TypeReference<>() {
            };
    private static final TypeReference<IntegrationEvent<OsEventPayloads.Cancelled>>
            OS_CANCELLED_TYPE = new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final QuoteSagaHandler handler;
    private final BillingMetrics metrics;

    public QuoteEventListener(
            ObjectMapper objectMapper,
            QuoteSagaHandler handler,
            BillingMetrics metrics
    ) {
        this.objectMapper = objectMapper;
        this.handler = handler;
        this.metrics = metrics;
    }

    @RabbitListener(queues = "${billing.messaging.queues.quote-requested}")
    public void onQuoteRequested(Message message) {
        IntegrationEvent<OsEventPayloads.QuoteRequested> event =
                read(message, QUOTE_REQUESTED_TYPE);
        validateSourceAndType(event, EventTypes.OS_QUOTE_REQUESTED);
        withMdc(event, () -> handler.handleQuoteRequested(event));
    }

    @RabbitListener(queues = "${billing.messaging.queues.os-cancelled}")
    public void onOsCancelled(Message message) {
        IntegrationEvent<OsEventPayloads.Cancelled> event =
                read(message, OS_CANCELLED_TYPE);
        validateSourceAndType(event, EventTypes.OS_CANCELLED);
        withMdc(event, () -> handler.handleOsCancelled(event));
    }

    private void withMdc(IntegrationEvent<?> event, Runnable action) {
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId());
        }
        if (event.eventId() != null) {
            MDC.put("eventId", event.eventId().toString());
        }
        try {
            action.run();
            metrics.messageConsumed(event.eventType(), "processed");
        } catch (RuntimeException exception) {
            metrics.messageConsumed(event.eventType(), "failed");
            throw exception;
        } finally {
            MDC.remove("correlationId");
            MDC.remove("eventId");
        }
    }

    private <T> IntegrationEvent<T> read(
            Message message,
            TypeReference<IntegrationEvent<T>> type
    ) {
        try {
            return objectMapper.readValue(message.getBody(), type);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid integration event",
                    exception
            );
        }
    }

    private void validateSourceAndType(IntegrationEvent<?> event, String eventType) {
        if (!eventType.equals(event.eventType()) || !"os-service".equals(event.source())) {
            throw new AmqpRejectAndDontRequeueException(
                    "Unexpected event type or source: " + event.eventType()
            );
        }
    }
}
