package br.com.fiap.mecanica.billing.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.shared.observability.BillingMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BillingMetrics metrics;

    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(
                repository,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                metrics
        );
    }

    @Test
    void shouldSerializeAndStorePendingEvent() throws Exception {
        var event = IntegrationEvent.billingEvent(
                EventTypes.QUOTE_CREATED,
                "OS-1",
                "payload",
                EventContext.started("OS-1"),
                NOW
        );
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"event\":\"value\"}");

        service.enqueue(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(event.eventId().toString());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventTypes.QUOTE_CREATED);
        assertThat(captor.getValue().getPayload()).isEqualTo("{\"event\":\"value\"}");
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void shouldFailWhenSerializationFails() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(
                new JsonProcessingException("invalid") {
                }
        );

        assertThatThrownBy(() -> service.enqueue(IntegrationEvent.billingEvent(
                EventTypes.QUOTE_CREATED,
                "OS-1",
                "payload",
                EventContext.started("OS-1"),
                NOW
        ))).isInstanceOf(IllegalStateException.class);
    }
}
