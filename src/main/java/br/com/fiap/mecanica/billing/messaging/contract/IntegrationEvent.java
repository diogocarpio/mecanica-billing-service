package br.com.fiap.mecanica.billing.messaging.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IntegrationEvent<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String source,
        String correlationId,
        UUID causationId,
        String osId,
        T payload
) {

    public IntegrationEvent {
        eventId = Objects.requireNonNull(eventId, "Event id is required");
        eventType = requireText(eventType, "Event type is required");
        if (eventVersion != 1) {
            throw new IllegalArgumentException("Only event version 1 is supported");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "Event timestamp is required");
        source = requireText(source, "Event source is required");
        correlationId = requireText(correlationId, "Correlation id is required");
        osId = requireText(osId, "OS id is required");
        payload = Objects.requireNonNull(payload, "Event payload is required");
    }

    public static <T> IntegrationEvent<T> billingEvent(
            String eventType,
            String osId,
            T payload,
            EventContext context,
            Instant occurredAt
    ) {
        Objects.requireNonNull(context, "Event context is required");
        return new IntegrationEvent<>(
                UUID.randomUUID(),
                eventType,
                1,
                occurredAt,
                "billing-service",
                context.correlationId(),
                context.causationId(),
                osId,
                payload
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
