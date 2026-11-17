package br.com.fiap.mecanica.billing.quote.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;

public record CreateQuoteCommand(
        String osId,
        String customerId,
        List<Item> items,
        String currency,
        Instant validUntil
) {

    public record Item(
            String description,
            QuoteItemType type,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {
    }
}
