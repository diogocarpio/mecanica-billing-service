package br.com.fiap.mecanica.billing.quote.application;

public class QuoteAlreadyExistsException extends RuntimeException {

    public QuoteAlreadyExistsException(String osId) {
        super("A quote already exists for OS " + osId);
    }
}
