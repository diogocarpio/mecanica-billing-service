package br.com.fiap.mecanica.billing.quote.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateQuoteRequest(
        @NotBlank String osId,
        @NotBlank String customerId,
        @NotEmpty List<@Valid QuoteItemRequest> items,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull @Future Instant validUntil
) {
}
