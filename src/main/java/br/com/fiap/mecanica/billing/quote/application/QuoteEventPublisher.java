package br.com.fiap.mecanica.billing.quote.application;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.quote.domain.Quote;

public interface QuoteEventPublisher {

    void quoteCreated(Quote quote, EventContext context);

    void quoteSubmitted(Quote quote, EventContext context);

    void quoteApproved(Quote quote, EventContext context);

    void quoteRejected(Quote quote, EventContext context);

    void quoteCancelled(Quote quote, EventContext context);

    void quoteFailed(String osId, String code, String message, EventContext context);
}
