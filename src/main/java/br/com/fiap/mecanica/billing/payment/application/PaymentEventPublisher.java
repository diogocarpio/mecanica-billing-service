package br.com.fiap.mecanica.billing.payment.application;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.payment.domain.Payment;

public interface PaymentEventPublisher {

    void paymentPending(Payment payment, EventContext context);

    void paymentApproved(Payment payment, EventContext context);

    void paymentRejected(Payment payment, EventContext context);

    void paymentFailed(Payment payment, EventContext context);
}
