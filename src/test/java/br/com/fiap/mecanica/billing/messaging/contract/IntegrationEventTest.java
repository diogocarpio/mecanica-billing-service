package br.com.fiap.mecanica.billing.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IntegrationEventTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void shouldCreateBillingEventFromContext() {
        UUID causationId = UUID.randomUUID();
        var context = new EventContext(" OS-1 ", causationId);

        var event = IntegrationEvent.billingEvent(
                EventTypes.QUOTE_CREATED,
                "OS-1",
                "payload",
                context,
                NOW
        );

        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventType()).isEqualTo(EventTypes.QUOTE_CREATED);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.source()).isEqualTo("billing-service");
        assertThat(event.correlationId()).isEqualTo("OS-1");
        assertThat(event.causationId()).isEqualTo(causationId);
        assertThat(event.osId()).isEqualTo("OS-1");
        assertThat(event.payload()).isEqualTo("payload");
    }

    @Test
    void shouldCreateContexts() {
        UUID eventId = UUID.randomUUID();
        var event = new IntegrationEvent<>(
                eventId,
                EventTypes.OS_CANCELLED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                "payload"
        );

        assertThat(EventContext.started("OS-1").causationId()).isNull();
        assertThat(EventContext.from(event))
                .isEqualTo(new EventContext("OS-1", eventId));
    }

    @Test
    void shouldRejectBlankEnvelopeFields() {
        assertThatThrownBy(() -> new IntegrationEvent<>(
                null, "event", 1, NOW, "source", "corr", null, "OS-1", "payload"
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), " ", 1, NOW, "source", "corr", null, "OS-1", "payload"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, null, "source", "corr", null, "OS-1", "payload"
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, NOW, " ", "corr", null, "OS-1", "payload"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, NOW, "source", " ", null, "OS-1", "payload"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, NOW, "source", "corr", null, " ", "payload"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, NOW, "source", "corr", null, "OS-1", null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullEnvelopeFields() {
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), null, 1, NOW, "source", "corr", null, "OS-1", "payload"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, NOW, null, "corr", null, "OS-1", "payload"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, NOW, "source", null, null, "OS-1", "payload"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(), "event", 1, NOW, "source", "corr", null, null, "payload"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullContextInBillingEvent() {
        assertThatThrownBy(() -> IntegrationEvent.billingEvent(
                EventTypes.QUOTE_CREATED, "OS-1", "payload", null, NOW
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectInvalidEnvelope() {
        assertThatThrownBy(() -> new IntegrationEvent<>(
                UUID.randomUUID(),
                "event",
                2,
                NOW,
                "source",
                "correlation",
                null,
                "OS-1",
                "payload"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EventContext.started(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventContext.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}
