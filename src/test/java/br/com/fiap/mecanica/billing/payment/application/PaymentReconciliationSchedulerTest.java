package br.com.fiap.mecanica.billing.payment.application;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    @Mock
    private PaymentService paymentService;

    @Test
    void shouldDelegateReconciliationToPaymentService() {
        var scheduler = new PaymentReconciliationScheduler(paymentService);

        scheduler.reconcile();

        verify(paymentService).reconcilePendingPayments();
    }
}
