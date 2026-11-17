package br.com.fiap.mecanica.billing.quote.application;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(String identifier) {
        super("Quote not found: " + identifier);
    }
}
