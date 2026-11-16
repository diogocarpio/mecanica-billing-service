package br.com.fiap.mecanica.billing.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;

public final class OsEventPayloads {

    private OsEventPayloads() {
    }

    public record QuoteRequested(
            String customerId,
            List<QuoteItem> items,
            String currency,
            Instant validUntil
    ) {
    }

    public record QuoteItem(
            String description,
            QuoteItemType type,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {
    }

    public record Cancelled(
            String reason,
            Instant cancelledAt
    ) {
    }
}
