package br.com.fiap.mecanica.billing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.OsEventPayloads;
import br.com.fiap.mecanica.billing.messaging.inbox.InboxEventRepository;
import br.com.fiap.mecanica.billing.messaging.outbox.OutboxEventRepository;
import br.com.fiap.mecanica.billing.payment.application.MercadoPagoGateway;
import br.com.fiap.mecanica.billing.payment.application.MercadoPagoWebhookSignatureValidator;
import br.com.fiap.mecanica.billing.payment.application.PaymentService;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import br.com.fiap.mecanica.billing.payment.infrastructure.PaymentRepository;
import br.com.fiap.mecanica.billing.quote.application.CreateQuoteCommand;
import br.com.fiap.mecanica.billing.quote.application.QuoteService;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.infrastructure.QuoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "billing.mercado-pago.reconciliation-enabled=false",
        "billing.messaging.outbox.poll-interval-ms=100"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BillingFlowIntegrationTest {

    @Container
    static final MongoDBContainer MONGODB =
            new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.data.mongodb.uri",
                () -> MONGODB.getReplicaSetUrl("billing")
        );
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired
    private QuoteService quoteService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private QuoteRepository quoteRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private InboxEventRepository inboxRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MercadoPagoGateway mercadoPagoGateway;
    @MockitoBean
    private MercadoPagoWebhookSignatureValidator signatureValidator;

    @BeforeEach
    void cleanDatabase() {
        inboxRepository.deleteAll();
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
        quoteRepository.deleteAll();
    }

    @Test
    void shouldPersistPaymentAndEventsAtomically() {
        String osId = "OS-INTEGRATION-" + UUID.randomUUID();
        var quote = quoteService.create(command(osId));
        quoteService.submit(quote.getId());
        quoteService.approve(quote.getId());

        when(mercadoPagoGateway.createPreference(any())).thenReturn(
                new MercadoPagoGateway.PreferenceResult(
                        "PREF-1",
                        "https://sandbox.test/PREF-1"
                )
        );
        var payment = paymentService.createPreference(
                quote.getId(),
                "payer@testuser.com"
        );
        var duplicate = paymentService.createPreference(
                quote.getId(),
                "payer@testuser.com"
        );
        when(mercadoPagoGateway.getPayment("MP-1")).thenReturn(
                new MercadoPagoGateway.ProviderPayment(
                        "MP-1",
                        payment.getId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        "approved",
                        "accredited"
                )
        );

        paymentService.handleWebhook("MP-1", "payment", "request-1", "signature");

        var approved = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(duplicate.getId()).isEqualTo(payment.getId());
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(outboxRepository.findAll())
                .extracting(event -> event.getEventType())
                .contains(
                        EventTypes.QUOTE_CREATED,
                        EventTypes.QUOTE_SUBMITTED,
                        EventTypes.QUOTE_APPROVED,
                        EventTypes.PAYMENT_PENDING,
                        EventTypes.PAYMENT_APPROVED
                );
    }

    @Test
    void shouldConsumeDuplicateQuoteRequestOnlyOnce() throws Exception {
        String osId = "OS-RABBIT-" + UUID.randomUUID();
        var event = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_QUOTE_REQUESTED,
                1,
                Instant.now(),
                "os-service",
                osId,
                null,
                osId,
                new OsEventPayloads.QuoteRequested(
                        "CUSTOMER-1",
                        List.of(new OsEventPayloads.QuoteItem(
                                "Diagnostic",
                                QuoteItemType.LABOR,
                                BigDecimal.ONE,
                                new BigDecimal("90.00")
                        )),
                        "BRL",
                        Instant.now().plus(Duration.ofHours(1))
                )
        );
        byte[] body = objectMapper.writeValueAsString(event)
                .getBytes(StandardCharsets.UTF_8);

        send(EventTypes.OS_QUOTE_REQUESTED, body);
        send(EventTypes.OS_QUOTE_REQUESTED, body);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(quoteRepository.findByOsId(osId)).isPresent();
            assertThat(inboxRepository.findById(event.eventId().toString())).isPresent();
        });
        assertThat(quoteRepository.findAll())
                .filteredOn(quote -> quote.getOsId().equals(osId))
                .hasSize(1);
    }

    private void send(String routingKey, byte[] body) {
        rabbitTemplate.send(
                "mecanica.events",
                routingKey,
                MessageBuilder.withBody(body)
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build()
        );
    }

    private CreateQuoteCommand command(String osId) {
        return new CreateQuoteCommand(
                osId,
                "CUSTOMER-1",
                List.of(new CreateQuoteCommand.Item(
                        "Diagnostic",
                        QuoteItemType.LABOR,
                        BigDecimal.ONE,
                        new BigDecimal("100.00")
                )),
                "BRL",
                Instant.now().plus(Duration.ofHours(1))
        );
    }
}
