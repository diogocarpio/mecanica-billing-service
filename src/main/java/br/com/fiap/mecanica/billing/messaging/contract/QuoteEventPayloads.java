package br.com.fiap.mecanica.billing.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;

public final class QuoteEventPayloads {

    private QuoteEventPayloads() {
    }

    public record Created(
            String quoteId,
            String customerId,
            BigDecimal total,
            String currency,
            QuoteStatus status,
            Instant validUntil
    ) {
    }

    public record Submitted(
            String quoteId,
            BigDecimal total,
            String currency,
            QuoteStatus status,
            Instant validUntil
    ) {
    }

    public record Approved(
            String quoteId,
            BigDecimal total,
            String currency,
            QuoteStatus status,
            Instant decidedAt
    ) {
    }

    public record Rejected(
            String quoteId,
            QuoteStatus status,
            String reason,
            Instant decidedAt
    ) {
    }

    public record Cancelled(
            String quoteId,
            QuoteStatus status,
            String reason,
            Instant cancelledAt
    ) {
    }

    public record Failed(
            String code,
            String message,
            Instant failedAt
    ) {
    }
}
