package br.com.fiap.mecanica.billing.messaging.outbox;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("outbox_events")
@CompoundIndex(
        name = "outbox_pending_idx",
        def = "{'status': 1, 'nextAttemptAt': 1, 'createdAt': 1}"
)
public class OutboxEvent {

    @Id
    private final String id;
    private final String eventType;
    private final String payload;
    private OutboxStatus status;
    private int attempts;
    private final Instant createdAt;
    private Instant nextAttemptAt;
    private Instant publishedAt;
    private String lastError;

    @PersistenceCreator
    public OutboxEvent(
            String id,
            String eventType,
            String payload,
            OutboxStatus status,
            int attempts,
            Instant createdAt,
            Instant nextAttemptAt,
            Instant publishedAt,
            String lastError
    ) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.nextAttemptAt = nextAttemptAt;
        this.publishedAt = publishedAt;
        this.lastError = lastError;
    }

    public static OutboxEvent pending(
            String id,
            String eventType,
            String payload,
            Instant now
    ) {
        return new OutboxEvent(
                id,
                eventType,
                payload,
                OutboxStatus.PENDING,
                0,
                now,
                now,
                null,
                null
        );
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        nextAttemptAt = null;
        lastError = null;
    }

    public void markFailedAttempt(
            Instant now,
            String error,
            int maxAttempts,
            long retryDelaySeconds
    ) {
        attempts++;
        lastError = error;
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
            nextAttemptAt = null;
            return;
        }
        nextAttemptAt = now.plusSeconds(retryDelaySeconds);
    }

    public String getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
