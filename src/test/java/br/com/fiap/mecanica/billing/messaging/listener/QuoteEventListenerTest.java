package br.com.fiap.mecanica.billing.messaging.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.OsEventPayloads;
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

    private Message message(Object event) throws Exception {
        return new Message(
                objectMapper.writeValueAsBytes(event),
                new MessageProperties()
        );
    }
}
