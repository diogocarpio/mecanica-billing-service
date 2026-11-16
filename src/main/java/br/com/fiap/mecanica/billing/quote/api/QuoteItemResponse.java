package br.com.fiap.mecanica.billing.quote.api;

import java.math.BigDecimal;

import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;

public record QuoteItemResponse(
        String description,
        QuoteItemType type,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {

    static QuoteItemResponse from(QuoteItem item) {
        return new QuoteItemResponse(
                item.description(),
                item.type(),
                item.quantity(),
                item.unitPrice(),
                item.subtotal()
        );
    }
}
