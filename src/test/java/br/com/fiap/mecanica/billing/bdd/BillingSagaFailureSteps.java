package br.com.fiap.mecanica.billing.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.OsEventPayloads;
import br.com.fiap.mecanica.billing.messaging.contract.QuoteEventPayloads;
import br.com.fiap.mecanica.billing.messaging.inbox.InboxProcessor;
import br.com.fiap.mecanica.billing.messaging.listener.QuoteSagaHandler;
import br.com.fiap.mecanica.billing.messaging.outbox.OutboxQuoteEventPublisher;
import br.com.fiap.mecanica.billing.messaging.outbox.OutboxService;
import br.com.fiap.mecanica.billing.quote.application.QuoteService;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import br.com.fiap.mecanica.billing.quote.infrastructure.QuoteRepository;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.mockito.ArgumentCaptor;

public class BillingSagaFailureSteps {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final String FAIL_OS_ID = "OS-BDD-FAIL";
    private static final String CANCEL_OS_ID = "OS-BDD-CANCEL";

    private final QuoteRepository quoteRepository = mock(QuoteRepository.class);
    private final InboxProcessor inboxProcessor = mock(InboxProcessor.class);
    private final OutboxService outboxService = mock(OutboxService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final OutboxQuoteEventPublisher eventPublisher =
            new OutboxQuoteEventPublisher(outboxService, clock);

    private final QuoteService quoteService = new QuoteService(
            quoteRepository,
            clock,
            eventPublisher
    );

    private final QuoteSagaHandler handler = new QuoteSagaHandler(
            inboxProcessor,
            quoteService,
            eventPublisher,
            clock
    );

    private IntegrationEvent<?> incomingEvent;
    private Quote quote;

    public BillingSagaFailureSteps() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        }).when(inboxProcessor).process(any(), any());
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Dado("uma solicitação de orçamento inválida para a OS {string}")
    public void invalidQuoteRequest(String osId) {
        incomingEvent = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_QUOTE_REQUESTED,
                1,
                NOW,
                "os-service",
                osId,
                null,
                osId,
                new OsEventPayloads.QuoteRequested(
                        "CUSTOMER-BDD-1",
                        List.of(),
                        "BRL",
                        NOW.plusSeconds(3600)
                )
        );
    }

    @Dado("um orçamento pendente de aprovação para a OS {string}")
    public void pendingQuote(String osId) {
        quote = Quote.create(
                osId,
                "CUSTOMER-BDD-1",
                List.of(new QuoteItem(
                        "Labor",
                        QuoteItemType.LABOR,
                        BigDecimal.ONE,
                        new BigDecimal("150.00")
                )),
                "BRL",
                NOW.plusSeconds(3600),
                NOW
        );
        quote.submit(NOW.plusSeconds(1));
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        when(quoteRepository.findByOsId(osId)).thenReturn(Optional.of(quote));
    }

    @Quando("o QuoteSagaHandler processa a solicitação")
    public void processQuoteRequest() {
        assertThat(handler.handleQuoteRequested(
                cast(incomingEvent)
        )).isTrue();
    }

    @Quando("o QuoteSagaHandler processa o cancelamento da OS")
    public void processOsCancellation() {
        incomingEvent = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_CANCELLED,
                1,
                NOW,
                "os-service",
                CANCEL_OS_ID,
                null,
                CANCEL_OS_ID,
                new OsEventPayloads.Cancelled("Customer cancelled", NOW)
        );
        assertThat(handler.handleOsCancelled(
                cast(incomingEvent)
        )).isTrue();
    }

    @Entao("o evento publicado é QUOTE_FAILED com código INVALID_QUOTE_DATA")
    public void failedEventPublished() {
        IntegrationEvent<?> published = capturedEvent();
        assertThat(published.eventType()).isEqualTo(EventTypes.QUOTE_FAILED);
        assertThat(published.osId()).isEqualTo(FAIL_OS_ID);
        assertThat(published.eventVersion()).isEqualTo(1);
        assertThat(published.payload())
                .isInstanceOf(QuoteEventPayloads.Failed.class);
        assertThat(((QuoteEventPayloads.Failed) published.payload()).code())
                .isEqualTo("INVALID_QUOTE_DATA");
    }

    @Entao("o orçamento fica com status CANCELLED")
    public void quoteCancelled() {
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
    }

    @Entao("o evento publicado é QUOTE_CANCELLED")
    public void cancelledEventPublished() {
        IntegrationEvent<?> published = capturedEvent();
        assertThat(published.eventType()).isEqualTo(EventTypes.QUOTE_CANCELLED);
        assertThat(published.osId()).isEqualTo(CANCEL_OS_ID);
        assertThat(published.payload())
                .isInstanceOf(QuoteEventPayloads.Cancelled.class);
        assertThat(((QuoteEventPayloads.Cancelled) published.payload()).status())
                .isEqualTo(QuoteStatus.CANCELLED);
    }

    private IntegrationEvent<?> capturedEvent() {
        ArgumentCaptor<IntegrationEvent> captor =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        org.mockito.Mockito.verify(outboxService).enqueue(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static <T> IntegrationEvent<T> cast(IntegrationEvent<?> event) {
        return (IntegrationEvent<T>) event;
    }
}
