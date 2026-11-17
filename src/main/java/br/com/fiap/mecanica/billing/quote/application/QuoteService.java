package br.com.fiap.mecanica.billing.quote.application;

import java.time.Clock;
import java.time.Instant;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.infrastructure.QuoteRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

    private final QuoteRepository repository;
    private final Clock clock;
    private final QuoteEventPublisher eventPublisher;

    public QuoteService(
            QuoteRepository repository,
            Clock clock,
            QuoteEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Quote create(CreateQuoteCommand command) {
        return create(command, EventContext.started(command.osId()));
    }

    @Transactional
    public Quote create(CreateQuoteCommand command, EventContext context) {
        if (repository.existsByOsId(command.osId())) {
            throw new QuoteAlreadyExistsException(command.osId());
        }

        var items = command.items().stream()
                .map(item -> new QuoteItem(
                        item.description(),
                        item.type(),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();

        Quote quote = Quote.create(
                command.osId(),
                command.customerId(),
                items,
                command.currency(),
                command.validUntil(),
                Instant.now(clock)
        );

        Quote savedQuote;
        try {
            savedQuote = repository.save(quote);
        } catch (DuplicateKeyException exception) {
            throw new QuoteAlreadyExistsException(command.osId());
        }
        eventPublisher.quoteCreated(savedQuote, context);
        return savedQuote;
    }

    public Quote findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new QuoteNotFoundException(id));
    }

    public Quote findByOsId(String osId) {
        return repository.findByOsId(osId)
                .orElseThrow(() -> new QuoteNotFoundException("OS " + osId));
    }

    public boolean existsByOsId(String osId) {
        return repository.existsByOsId(osId);
    }

    @Transactional
    public Quote submit(String id) {
        Quote quote = findById(id);
        return submit(quote, EventContext.started(quote.getOsId()));
    }

    @Transactional
    public Quote submit(String id, EventContext context) {
        Quote quote = findById(id);
        return submit(quote, context);
    }

    @Transactional
    public Quote approve(String id) {
        Quote quote = findById(id);
        EventContext context = EventContext.started(quote.getOsId());
        if (!quote.approve(Instant.now(clock))) {
            return quote;
        }
        Quote savedQuote = repository.save(quote);
        eventPublisher.quoteApproved(savedQuote, context);
        return savedQuote;
    }

    @Transactional
    public Quote reject(String id, String reason) {
        Quote quote = findById(id);
        EventContext context = EventContext.started(quote.getOsId());
        if (!quote.reject(reason, Instant.now(clock))) {
            return quote;
        }
        Quote savedQuote = repository.save(quote);
        eventPublisher.quoteRejected(savedQuote, context);
        return savedQuote;
    }

    @Transactional
    public Quote cancelByOsId(String osId, String reason, EventContext context) {
        Quote quote = findByOsId(osId);
        if (!quote.cancel(reason, Instant.now(clock))) {
            return quote;
        }
        Quote savedQuote = repository.save(quote);
        eventPublisher.quoteCancelled(savedQuote, context);
        return savedQuote;
    }

    private Quote submit(Quote quote, EventContext context) {
        if (!quote.submit(Instant.now(clock))) {
            return quote;
        }
        Quote savedQuote = repository.save(quote);
        eventPublisher.quoteSubmitted(savedQuote, context);
        return savedQuote;
    }
}
