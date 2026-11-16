package br.com.fiap.mecanica.billing.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BillingMetrics {

    private final MeterRegistry registry;

    public BillingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void eventEnqueued(String eventType) {
        counter("billing.events.enqueued", "eventType", eventType).increment();
    }

    public void outboxPublish(boolean success, String eventType) {
        counter(
                "billing.outbox.publish",
                "result", success ? "success" : "failure",
                "eventType", eventType
        ).increment();
    }

    public void outboxFailedPermanently(String eventType) {
        counter("billing.outbox.failed", "eventType", eventType).increment();
    }

    public void messageConsumed(String eventType, String outcome) {
        counter(
                "billing.messaging.consumed",
                "eventType", eventType,
                "outcome", outcome
        ).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
