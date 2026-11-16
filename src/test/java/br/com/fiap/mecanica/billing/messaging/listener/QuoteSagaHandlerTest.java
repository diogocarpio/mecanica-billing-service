package br.com.fiap.mecanica.billing.messaging.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.OsEventPayloads;
import br.com.fiap.mecanica.billing.messaging.inbox.InboxProcessor;
import br.com.fiap.mecanica.billing.quote.application.CreateQuoteCommand;
import br.com.fiap.mecanica.billing.quote.application.QuoteEventPublisher;
import br.com.fiap.mecanica.billing.quote.application.QuoteService;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteSagaHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private InboxProcessor inboxProcessor;

    @Mock
    private QuoteService quoteService;

    @Mock
    private QuoteEventPublisher eventPublisher;

    private QuoteSagaHandler handler;

    @BeforeEach
    void setUp() {
        handler = new QuoteSagaHandler(
                inboxProcessor,
                quoteService,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        }).when(inboxProcessor).process(any(), any());
    }

    @Test
    void shouldCreateAndSubmitRequestedQuote() {
        var event = quoteRequestedEvent();
        Quote quote = storedQuote();
        when(quoteService.create(any(CreateQuoteCommand.class), any(EventContext.class)))
                .thenReturn(quote);

        assertThat(handler.handleQuoteRequested(event)).isTrue();

        ArgumentCaptor<CreateQuoteCommand> command =
                ArgumentCaptor.forClass(CreateQuoteCommand.class);
        ArgumentCaptor<EventContext> context = ArgumentCaptor.forClass(EventContext.class);
        verify(quoteService).create(command.capture(), context.capture());
        assertThat(command.getValue().osId()).isEqualTo("OS-1");
        assertThat(command.getValue().items()).hasSize(1);
        assertThat(context.getValue().causationId()).isEqualTo(event.eventId());
        verify(quoteService).submit("QUOTE-1", context.getValue());
    }

    @Test
    void shouldCancelQuoteFromOsEvent() {
        var event = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_CANCELLED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.Cancelled("Customer request", NOW)
        );

        assertThat(handler.handleOsCancelled(event)).isTrue();

        verify(quoteService).cancelByOsId(
                org.mockito.ArgumentMatchers.eq("OS-1"),
                org.mockito.ArgumentMatchers.eq("Customer request"),
                any(EventContext.class)
        );
    }

    @Test
    void shouldPublishFailureForInvalidRequest() {
        var invalidEvent = new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_QUOTE_REQUESTED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.QuoteRequested(
                        "CUSTOMER-1",
                        List.of(),
                        "BRL",
                        NOW.plusSeconds(3600)
                )
        );

        assertThat(handler.handleQuoteRequested(invalidEvent)).isTrue();

        verify(eventPublisher).quoteFailed(
                org.mockito.ArgumentMatchers.eq("OS-1"),
                org.mockito.ArgumentMatchers.eq("INVALID_QUOTE_DATA"),
                any(String.class),
                any(EventContext.class)
        );
    }

    @Test
    void shouldPublishFailureWhenQuoteAlreadyExists() {
        var event = quoteRequestedEvent();
        when(quoteService.existsByOsId("OS-1")).thenReturn(true);

        assertThat(handler.handleQuoteRequested(event)).isTrue();

        verify(eventPublisher).quoteFailed(
                org.mockito.ArgumentMatchers.eq("OS-1"),
                org.mockito.ArgumentMatchers.eq("QUOTE_CONFLICT"),
                any(String.class),
                any(EventContext.class)
        );
    }

    private IntegrationEvent<OsEventPayloads.QuoteRequested> quoteRequestedEvent() {
        return new IntegrationEvent<>(
                UUID.randomUUID(),
                EventTypes.OS_QUOTE_REQUESTED,
                1,
                NOW,
                "os-service",
                "OS-1",
                null,
                "OS-1",
                new OsEventPayloads.QuoteRequested(
                        "CUSTOMER-1",
                        List.of(new OsEventPayloads.QuoteItem(
                                "Labor",
                                QuoteItemType.LABOR,
                                BigDecimal.ONE,
                                new BigDecimal("50.00")
                        )),
                        "BRL",
                        NOW.plusSeconds(3600)
                )
        );
    }

    private Quote storedQuote() {
        return new Quote(
                "QUOTE-1",
                "OS-1",
                "CUSTOMER-1",
                List.of(new QuoteItem(
                        "Labor",
                        QuoteItemType.LABOR,
                        BigDecimal.ONE,
                        new BigDecimal("50.00")
                )),
                new BigDecimal("50.00"),
                "BRL",
                QuoteStatus.DRAFT,
                NOW.plusSeconds(3600),
                NOW,
                NOW,
                null,
                null,
                null,
                0L
        );
    }
}
