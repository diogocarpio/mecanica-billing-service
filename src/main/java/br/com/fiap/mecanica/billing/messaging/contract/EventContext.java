package br.com.fiap.mecanica.billing.messaging.contract;

import java.util.Objects;
import java.util.UUID;

public record EventContext(
        String correlationId,
        UUID causationId
) {

    public EventContext {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Correlation id is required");
        }
        correlationId = correlationId.trim();
    }

    public static EventContext started(String correlationId) {
        return new EventContext(correlationId, null);
    }

    public static EventContext from(IntegrationEvent<?> event) {
        Objects.requireNonNull(event, "Integration event is required");
        return new EventContext(event.correlationId(), event.eventId());
    }
}
