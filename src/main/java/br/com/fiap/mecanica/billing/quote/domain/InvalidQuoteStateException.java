package br.com.fiap.mecanica.billing.quote.domain;

public class InvalidQuoteStateException extends RuntimeException {

    public InvalidQuoteStateException(String message) {
        super(message);
    }
}
