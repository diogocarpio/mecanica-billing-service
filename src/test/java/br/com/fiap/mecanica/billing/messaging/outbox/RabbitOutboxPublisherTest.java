package br.com.fiap.mecanica.billing.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import br.com.fiap.mecanica.billing.shared.observability.BillingMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private BillingMetrics metrics;

    private RabbitOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RabbitOutboxPublisher(
                repository,
                rabbitTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                metrics,
                "mecanica.events",
                3,
                1
        );
    }

    @Test
    void shouldPublishAndMarkEvent() {
        OutboxEvent event = OutboxEvent.pending("1", "event.v1", "{}", NOW);
        when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                NOW
        )).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq("mecanica.events"),
                eq("event.v1"),
                any(Message.class),
                any(CorrelationData.class)
        );

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        verify(repository).save(event);
    }

    @Test
    void shouldScheduleRetryWhenBrokerFails() {
        OutboxEvent event = OutboxEvent.pending("1", "event.v1", "{}", NOW);
        when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                NOW
        )).thenReturn(List.of(event));
        doThrow(new AmqpException("down")).when(rabbitTemplate).send(
                eq("mecanica.events"),
                eq("event.v1"),
                any(Message.class),
                any(CorrelationData.class)
        );

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        verify(repository).save(event);
    }
}
