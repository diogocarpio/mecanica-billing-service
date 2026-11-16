package br.com.fiap.mecanica.billing.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void shouldMarkEventAsPublished() {
        OutboxEvent event = OutboxEvent.pending("1", "event.v1", "{}", NOW);

        event.markPublished(NOW.plusSeconds(1));

        assertThat(event.getId()).isEqualTo("1");
        assertThat(event.getEventType()).isEqualTo("event.v1");
        assertThat(event.getPayload()).isEqualTo("{}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getCreatedAt()).isEqualTo(NOW);
        assertThat(event.getNextAttemptAt()).isNull();
        assertThat(event.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void shouldScheduleRetryAndEventuallyFail() {
        OutboxEvent event = OutboxEvent.pending("1", "event.v1", "{}", NOW);

        event.markFailedAttempt(NOW, "down", 2, 5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(event.getLastError()).isEqualTo("down");

        event.markFailedAttempt(NOW.plusSeconds(5), "still down", 2, 10);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getNextAttemptAt()).isNull();
    }
}
