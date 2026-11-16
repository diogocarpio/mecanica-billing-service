package br.com.fiap.mecanica.billing.messaging.contract;

public final class EventTypes {

    public static final String OS_QUOTE_REQUESTED = "os.quote.requested.v1";
    public static final String OS_CANCELLED = "os.cancelled.v1";
    public static final String QUOTE_CREATED = "billing.quote.created.v1";
    public static final String QUOTE_SUBMITTED = "billing.quote.submitted.v1";
    public static final String QUOTE_APPROVED = "billing.quote.approved.v1";
    public static final String QUOTE_REJECTED = "billing.quote.rejected.v1";
    public static final String QUOTE_CANCELLED = "billing.quote.cancelled.v1";
    public static final String QUOTE_FAILED = "billing.quote.failed.v1";
    public static final String PAYMENT_PENDING = "billing.payment.pending.v1";
    public static final String PAYMENT_APPROVED = "billing.payment.approved.v1";
    public static final String PAYMENT_REJECTED = "billing.payment.rejected.v1";
    public static final String PAYMENT_FAILED = "billing.payment.failed.v1";

    private EventTypes() {
    }
}
