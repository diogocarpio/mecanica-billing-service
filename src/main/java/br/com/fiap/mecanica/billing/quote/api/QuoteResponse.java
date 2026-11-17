package br.com.fiap.mecanica.billing.quote.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;

public record QuoteResponse(
        String id,
        String osId,
        String customerId,
        List<QuoteItemResponse> items,
        BigDecimal total,
        String currency,
        QuoteStatus status,
        Instant validUntil,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant decidedAt,
        String rejectionReason
) {

    static QuoteResponse from(Quote quote) {
        return new QuoteResponse(
                quote.getId(),
                quote.getOsId(),
                quote.getCustomerId(),
                quote.getItems().stream().map(QuoteItemResponse::from).toList(),
                quote.getTotal(),
                quote.getCurrency(),
                quote.getStatus(),
                quote.getValidUntil(),
                quote.getCreatedAt(),
                quote.getUpdatedAt(),
                quote.getSubmittedAt(),
                quote.getDecidedAt(),
                quote.getRejectionReason()
        );
    }
}
