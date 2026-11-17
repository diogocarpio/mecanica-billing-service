package br.com.fiap.mecanica.billing.quote.domain;

public enum QuoteStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED
}
