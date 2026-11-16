package br.com.fiap.mecanica.billing.quote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectQuoteRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
