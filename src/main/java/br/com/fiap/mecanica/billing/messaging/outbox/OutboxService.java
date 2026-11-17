package br.com.fiap.mecanica.billing.messaging.outbox;

import java.time.Clock;
import java.time.Instant;

import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.shared.observability.BillingMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final BillingMetrics metrics;

    public OutboxService(
            OutboxEventRepository repository,
            ObjectMapper objectMapper,
            Clock clock,
            BillingMetrics metrics
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    public void enqueue(IntegrationEvent<?> event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            repository.save(OutboxEvent.pending(
                    event.eventId().toString(),
                    event.eventType(),
                    payload,
                    Instant.now(clock)
            ));
            metrics.eventEnqueued(event.eventType());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event", exception);
        }
    }
}
