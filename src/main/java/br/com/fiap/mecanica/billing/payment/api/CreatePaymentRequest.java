package br.com.fiap.mecanica.billing.payment.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotBlank String quoteId,
        @NotBlank @Email @Size(max = 254) String payerEmail
) {
}
