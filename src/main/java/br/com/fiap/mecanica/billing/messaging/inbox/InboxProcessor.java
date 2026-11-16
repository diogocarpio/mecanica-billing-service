package br.com.fiap.mecanica.billing.messaging.inbox;

import java.time.Clock;
import java.time.Instant;

import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxProcessor {

    private final InboxEventRepository repository;
    private final Clock clock;

    public InboxProcessor(InboxEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean process(IntegrationEvent<?> event, Runnable action) {
        String eventId = event.eventId().toString();
        if (repository.existsById(eventId)) {
            return false;
        }

        action.run();
        repository.save(new InboxEvent(
                eventId,
                event.eventType(),
                event.correlationId(),
                Instant.now(clock)
        ));
        return true;
    }
}
