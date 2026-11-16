package br.com.fiap.mecanica.billing.messaging.inbox;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("inbox_events")
public class InboxEvent {

    @Id
    private final String id;
    private final String eventType;
    private final String correlationId;
    private final Instant processedAt;

    @PersistenceCreator
    public InboxEvent(
            String id,
            String eventType,
            String correlationId,
            Instant processedAt
    ) {
        this.id = id;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
