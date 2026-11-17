package br.com.fiap.mecanica.billing.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.messaging.contract.EventTypes;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
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
class OutboxQuoteEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private OutboxService outboxService;

    private OutboxQuoteEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxQuoteEventPublisher(
                outboxService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateEveryQuoteEventEnvelope() {
        EventContext context = EventContext.started("OS-1");

        publisher.quoteCreated(quote(QuoteStatus.DRAFT), context);
        publisher.quoteSubmitted(quote(QuoteStatus.PENDING_APPROVAL), context);
        publisher.quoteApproved(quote(QuoteStatus.APPROVED), context);
        publisher.quoteRejected(quote(QuoteStatus.REJECTED), context);
        publisher.quoteCancelled(quote(QuoteStatus.CANCELLED), context);
        publisher.quoteFailed("OS-1", "PROCESSING_ERROR", "failure", context);

        ArgumentCaptor<IntegrationEvent<?>> captor =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outboxService, times(6)).enqueue(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(IntegrationEvent::eventType)
                .containsExactly(
                        EventTypes.QUOTE_CREATED,
                        EventTypes.QUOTE_SUBMITTED,
                        EventTypes.QUOTE_APPROVED,
                        EventTypes.QUOTE_REJECTED,
                        EventTypes.QUOTE_CANCELLED,
                        EventTypes.QUOTE_FAILED
                );
        assertThat(captor.getAllValues())
                .allMatch(event -> event.source().equals("billing-service"));
    }

    private Quote quote(QuoteStatus status) {
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
                status,
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60),
                NOW,
                NOW.minusSeconds(30),
                NOW,
                status == QuoteStatus.REJECTED || status == QuoteStatus.CANCELLED
                        ? "reason"
                        : null,
                1L
        );
    }
}
