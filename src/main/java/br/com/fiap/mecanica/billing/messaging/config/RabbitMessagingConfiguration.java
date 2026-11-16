package br.com.fiap.mecanica.billing.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RabbitMessagingConfiguration {

    @Bean
    TopicExchange domainExchange(
            @Value("${billing.messaging.exchange}") String exchange
    ) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    TopicExchange deadLetterExchange(
            @Value("${billing.messaging.dead-letter-exchange}") String exchange
    ) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    Queue quoteRequestedQueue(
            @Value("${billing.messaging.queues.quote-requested}") String queue,
            @Value("${billing.messaging.dead-letter-exchange}") String deadLetterExchange
    ) {
        return durableQueue(queue, deadLetterExchange);
    }

    @Bean
    Binding quoteRequestedBinding(
            Queue quoteRequestedQueue,
            TopicExchange domainExchange,
            @Value("${billing.messaging.routing-keys.quote-requested}") String routingKey
    ) {
        return BindingBuilder.bind(quoteRequestedQueue).to(domainExchange).with(routingKey);
    }

    @Bean
    Queue quoteRequestedDeadLetterQueue(
            @Value("${billing.messaging.queues.quote-requested}") String queue
    ) {
        return QueueBuilder.durable(queue + ".dlq").build();
    }

    @Bean
    Binding quoteRequestedDeadLetterBinding(
            Queue quoteRequestedDeadLetterQueue,
            TopicExchange deadLetterExchange,
            @Value("${billing.messaging.queues.quote-requested}") String queue
    ) {
        return BindingBuilder
                .bind(quoteRequestedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(queue + ".dlq");
    }

    @Bean
    Queue osCancelledQueue(
            @Value("${billing.messaging.queues.os-cancelled}") String queue,
            @Value("${billing.messaging.dead-letter-exchange}") String deadLetterExchange
    ) {
        return durableQueue(queue, deadLetterExchange);
    }

    @Bean
    Binding osCancelledBinding(
            Queue osCancelledQueue,
            TopicExchange domainExchange,
            @Value("${billing.messaging.routing-keys.os-cancelled}") String routingKey
    ) {
        return BindingBuilder.bind(osCancelledQueue).to(domainExchange).with(routingKey);
    }

    @Bean
    Queue osCancelledDeadLetterQueue(
            @Value("${billing.messaging.queues.os-cancelled}") String queue
    ) {
        return QueueBuilder.durable(queue + ".dlq").build();
    }

    @Bean
    Binding osCancelledDeadLetterBinding(
            Queue osCancelledDeadLetterQueue,
            TopicExchange deadLetterExchange,
            @Value("${billing.messaging.queues.os-cancelled}") String queue
    ) {
        return BindingBuilder
                .bind(osCancelledDeadLetterQueue)
                .to(deadLetterExchange)
                .with(queue + ".dlq");
    }

    private Queue durableQueue(String name, String deadLetterExchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }
}
