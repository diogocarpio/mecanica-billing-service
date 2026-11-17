package br.com.fiap.mecanica.billing.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class RabbitMessagingConfigurationTest {

    private final RabbitMessagingConfiguration config = new RabbitMessagingConfiguration();

    @Test
    void shouldDeclareExchangesQueuesAndBindings() {
        TopicExchange domain = config.domainExchange("mecanica.events");
        TopicExchange deadLetter = config.deadLetterExchange("mecanica.events.dlx");

        Queue quoteRequested = config.quoteRequestedQueue(
                "billing.quote.requested", "mecanica.events.dlx"
        );
        Binding quoteRequestedBinding = config.quoteRequestedBinding(
                quoteRequested, domain, "os.quote.requested.v1"
        );
        Queue quoteRequestedDlq = config.quoteRequestedDeadLetterQueue(
                "billing.quote.requested"
        );
        Binding quoteRequestedDlqBinding = config.quoteRequestedDeadLetterBinding(
                quoteRequestedDlq, deadLetter, "billing.quote.requested"
        );

        Queue osCancelled = config.osCancelledQueue(
                "billing.os.cancelled", "mecanica.events.dlx"
        );
        Binding osCancelledBinding = config.osCancelledBinding(
                osCancelled, domain, "os.cancelled.v1"
        );
        Queue osCancelledDlq = config.osCancelledDeadLetterQueue("billing.os.cancelled");
        Binding osCancelledDlqBinding = config.osCancelledDeadLetterBinding(
                osCancelledDlq, deadLetter, "billing.os.cancelled"
        );

        assertThat(domain.getName()).isEqualTo("mecanica.events");
        assertThat(deadLetter.getName()).isEqualTo("mecanica.events.dlx");
        assertThat(quoteRequested.getName()).isEqualTo("billing.quote.requested");
        assertThat(quoteRequestedBinding.getRoutingKey()).isEqualTo("os.quote.requested.v1");
        assertThat(quoteRequestedDlq.getName()).isEqualTo("billing.quote.requested.dlq");
        assertThat(quoteRequestedDlqBinding.getRoutingKey())
                .isEqualTo("billing.quote.requested.dlq");
        assertThat(osCancelled.getName()).isEqualTo("billing.os.cancelled");
        assertThat(osCancelledBinding.getRoutingKey()).isEqualTo("os.cancelled.v1");
        assertThat(osCancelledDlq.getName()).isEqualTo("billing.os.cancelled.dlq");
        assertThat(osCancelledDlqBinding.getRoutingKey())
                .isEqualTo("billing.os.cancelled.dlq");
    }
}
