package br.com.fiap.mecanica.billing.payment.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "billing.mercado-pago.reconciliation-enabled",
        havingValue = "true"
)
public class PaymentReconciliationScheduler {

    private final PaymentService paymentService;

    public PaymentReconciliationScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${billing.mercado-pago.reconciliation-interval-ms:60000}")
    public void reconcile() {
        paymentService.reconcilePendingPayments();
    }
}
