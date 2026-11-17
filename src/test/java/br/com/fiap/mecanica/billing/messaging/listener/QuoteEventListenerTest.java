package br.com.fiap.mecanica.billing.messaging.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.OsEventPayloads;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.shared.observability.BillingMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class QuoteEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private QuoteSagaHandler handler;

    @Mock
    private BillingMetrics metrics;

    private ObjectMapper objectMapper;
    private QuoteEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new QuoteEventListener(objectMapper, handler, metrics);
    }

    @Test
    void shouldDeserializeCancellationEvent() throws Exception {
        var event = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_CANCELLED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.Cancelled("Customer request", NOW)
        );

        listener.onOsCancelled(message(event));

        verify(handler).handleOsCancelled(event);
    }

    @Test
    void shouldRejectMalformedOrUnexpectedEvent() throws Exception {
        assertThatThrownBy(() -> listener.onOsCancelled(
                new Message("not-json".getBytes(), new MessageProperties())
        )).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        var event = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_CANCELLED,
                1,
                NOW,
                "wrong-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.Cancelled("Customer request", NOW)
        );
        assertThatThrownBy(() -> listener.onOsCancelled(message(event)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void shouldHandleQuoteRequestedEvent() throws Exception {
        var event = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_QUOTE_REQUESTED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.QuoteRequested(
                        "CUSTOMER-1",
                        List.of(new OsEventPayloads.QuoteItem(
                                "Labor",
                                QuoteItemType.LABOR,
                                BigDecimal.ONE,
                                new BigDecimal("50.00")
                        )),
                        "BRL",
                        NOW.plusSeconds(3600)
                )
        );

        listener.onQuoteRequested(message(event));

        verify(handler).handleQuoteRequested(event);
    }

    @Test
    void shouldRecordFailedMetricWhenHandlerThrows() throws Exception {
        var event = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_CANCELLED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.Cancelled("Customer request", NOW)
        );
        doThrow(new RuntimeException("boom")).when(handler).handleOsCancelled(event);

        assertThatThrownBy(() -> listener.onOsCancelled(message(event)))
                .isInstanceOf(RuntimeException.class);

        verify(metrics).messageConsumed(EventTypes.OS_CANCELLED, "failed");
    }

    @Test
    void shouldRejectEventWithWrongTypeButValidSource() throws Exception {
        var event = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.QUOTE_CREATED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.Cancelled("Customer request", NOW)
        );

        assertThatThrownBy(() -> listener.onOsCancelled(message(event)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private Message message(Object event) throws Exception {
        return new Message(
                objectMapper.writeValueAsBytes(event),
                new MessageProperties()
        );
    }
}
