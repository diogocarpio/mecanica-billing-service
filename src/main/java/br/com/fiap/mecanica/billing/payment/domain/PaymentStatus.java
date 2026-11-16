package br.com.fiap.mecanica.billing.payment.domain;

public enum PaymentStatus {
    PENDING,
    IN_PROCESS,
    APPROVED,
    REJECTED,
    CANCELLED,
    ERROR
}
