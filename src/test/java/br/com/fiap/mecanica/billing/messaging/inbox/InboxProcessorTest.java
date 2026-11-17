package br.com.fiap.mecanica.billing.messaging.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboxProcessorTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private InboxEventRepository repository;

    private InboxProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InboxProcessor(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldProcessAndRecordNewEvent() {
        IntegrationEvent<String> event = event();
        when(repository.existsById(event.eventId().toString())).thenReturn(false);
        AtomicBoolean actionExecuted = new AtomicBoolean();

        boolean processed = processor.process(event, () -> actionExecuted.set(true));

        assertThat(processed).isTrue();
        assertThat(actionExecuted).isTrue();
        ArgumentCaptor<InboxEvent> captor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(event.eventId().toString());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventTypes.OS_CANCELLED);
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("OS-1");
        assertThat(captor.getValue().getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldIgnoreDuplicateEvent() {
        IntegrationEvent<String> event = event();
        when(repository.existsById(event.eventId().toString())).thenReturn(true);
        AtomicBoolean actionExecuted = new AtomicBoolean();

        boolean processed = processor.process(event, () -> actionExecuted.set(true));

        assertThat(processed).isFalse();
        assertThat(actionExecuted).isFalse();
        verify(repository, never()).save(any());
    }

    private IntegrationEvent<String> event() {
        return new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_CANCELLED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                "payload"
        );
    }
}
