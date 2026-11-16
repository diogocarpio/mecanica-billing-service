package br.com.fiap.mecanica.billing.quote.api;

import java.math.BigDecimal;

import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuoteItemRequest(
        @NotBlank String description,
        @NotNull QuoteItemType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 3)
        BigDecimal quantity,
        @NotNull @DecimalMin("0.0") @Digits(integer = 12, fraction = 2)
        BigDecimal unitPrice
) {
}
