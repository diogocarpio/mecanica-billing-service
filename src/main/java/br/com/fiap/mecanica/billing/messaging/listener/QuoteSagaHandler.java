package br.com.fiap.mecanica.billing.messaging.listener;

import java.time.Clock;
import java.time.Instant;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.messaging.contract.IntegrationEvent;
import br.com.fiap.mecanica.billing.messaging.contract.OsEventPayloads;
import br.com.fiap.mecanica.billing.messaging.inbox.InboxProcessor;
import br.com.fiap.mecanica.billing.quote.application.CreateQuoteCommand;
import br.com.fiap.mecanica.billing.quote.application.QuoteEventPublisher;
import br.com.fiap.mecanica.billing.quote.application.QuoteService;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import org.springframework.stereotype.Service;

@Service
public class QuoteSagaHandler {

    private final InboxProcessor inboxProcessor;
    private final QuoteService quoteService;
    private final QuoteEventPublisher eventPublisher;
    private final Clock clock;

    public QuoteSagaHandler(
            InboxProcessor inboxProcessor,
            QuoteService quoteService,
            QuoteEventPublisher eventPublisher,
            Clock clock
    ) {
        this.inboxProcessor = inboxProcessor;
        this.quoteService = quoteService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public boolean handleQuoteRequested(
            IntegrationEvent<OsEventPayloads.QuoteRequested> event
    ) {
        CreateQuoteCommand command;
        try {
            command = createCommand(event);
            validate(command);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return recordFailure(event, "INVALID_QUOTE_DATA", exception.getMessage());
        }

        if (quoteService.existsByOsId(event.osId())) {
            return recordFailure(
                    event,
                    "QUOTE_CONFLICT",
                    "A quote already exists for OS " + event.osId()
            );
        }

        CreateQuoteCommand validCommand = command;
        return inboxProcessor.process(event, () -> {
            EventContext context = EventContext.from(event);
            var quote = quoteService.create(validCommand, context);
            quoteService.submit(quote.getId(), context);
        });
    }

    public boolean handleOsCancelled(
            IntegrationEvent<OsEventPayloads.Cancelled> event
    ) {
        return inboxProcessor.process(event, () -> quoteService.cancelByOsId(
                event.osId(),
                event.payload().reason(),
                EventContext.from(event)
        ));
    }

    private CreateQuoteCommand createCommand(
            IntegrationEvent<OsEventPayloads.QuoteRequested> event
    ) {
        OsEventPayloads.QuoteRequested payload = event.payload();
        return new CreateQuoteCommand(
                event.osId(),
                payload.customerId(),
                payload.items().stream()
                        .map(item -> new CreateQuoteCommand.Item(
                                item.description(),
                                item.type(),
                                item.quantity(),
                                item.unitPrice()
                        ))
                        .toList(),
                payload.currency(),
                payload.validUntil()
        );
    }

    private void validate(CreateQuoteCommand command) {
        var items = command.items().stream()
                .map(item -> new QuoteItem(
                        item.description(),
                        item.type(),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();
        Quote.create(
                command.osId(),
                command.customerId(),
                items,
                command.currency(),
                command.validUntil(),
                Instant.now(clock)
        );
    }

    private boolean recordFailure(
            IntegrationEvent<?> event,
            String code,
            String message
    ) {
        return inboxProcessor.process(event, () -> eventPublisher.quoteFailed(
                event.osId(),
                code,
                message == null ? code : message,
                EventContext.from(event)
        ));
    }
}
