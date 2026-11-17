package br.com.fiap.mecanica.billing.payment.application;

public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException(String message) {
        super(message);
    }
}
