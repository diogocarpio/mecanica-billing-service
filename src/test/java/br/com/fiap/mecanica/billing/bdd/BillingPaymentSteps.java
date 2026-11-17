package br.com.fiap.mecanica.billing.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import br.com.fiap.mecanica.billing.payment.application.MercadoPagoGateway;
import br.com.fiap.mecanica.billing.payment.application.MercadoPagoWebhookSignatureValidator;
import br.com.fiap.mecanica.billing.payment.application.PaymentPersistenceService;
import br.com.fiap.mecanica.billing.payment.application.PaymentService;
import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import br.com.fiap.mecanica.billing.payment.infrastructure.PaymentRepository;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.infrastructure.QuoteRepository;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;

public class BillingPaymentSteps {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private final QuoteRepository quoteRepository = mock(QuoteRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentPersistenceService persistenceService =
            mock(PaymentPersistenceService.class);
    private final MercadoPagoGateway gateway = mock(MercadoPagoGateway.class);
    private final MercadoPagoWebhookSignatureValidator signatureValidator =
            mock(MercadoPagoWebhookSignatureValidator.class);
    private final PaymentService paymentService = new PaymentService(
            quoteRepository,
            paymentRepository,
            persistenceService,
            gateway,
            signatureValidator,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(5)
    );

    private Quote quote;
    private Payment payment;
    private Payment duplicate;

    @Dado("um orçamento aprovado no valor de {string} BRL")
    public void approvedQuote(String amount) {
        quote = Quote.create(
                "OS-BDD-1",
                "CUSTOMER-BDD-1",
                List.of(new QuoteItem(
                        "Repair",
                        QuoteItemType.LABOR,
                        BigDecimal.ONE,
                        new BigDecimal(amount)
                )),
                "BRL",
                NOW.plusSeconds(3600),
                NOW
        );
        quote.submit(NOW.plusSeconds(1));
        quote.approve(NOW.plusSeconds(2));
        payment = Payment.create(
                quote.getId() == null ? "QUOTE-BDD-1" : quote.getId(),
                quote.getOsId(),
                quote.getTotal(),
                quote.getCurrency(),
                NOW.plusSeconds(3)
        );
        when(quoteRepository.findById("QUOTE-BDD-1")).thenReturn(Optional.of(quote));
        when(persistenceService.createOrGet(quote)).thenReturn(payment);
        when(gateway.createPreference(any())).thenReturn(
                new MercadoPagoGateway.PreferenceResult(
                        "PREF-BDD-1",
                        "https://sandbox.test/PREF-BDD-1"
                )
        );
        when(persistenceService.attachPreference(any(), any())).thenAnswer(invocation -> {
            var preference = invocation.<MercadoPagoGateway.PreferenceResult>getArgument(1);
            payment.attachPreference(
                    preference.preferenceId(),
                    preference.checkoutUrl(),
                    NOW.plusSeconds(4)
            );
            return payment;
        });
    }

    @Quando("o Billing cria duas vezes o checkout para o mesmo orçamento")
    public void createCheckoutTwice() {
        payment = paymentService.createPreference(
                "QUOTE-BDD-1",
                "buyer@testuser.com"
        );
        duplicate = paymentService.createPreference(
                "QUOTE-BDD-1",
                "buyer@testuser.com"
        );
    }

    @Quando("o Mercado Pago notifica um pagamento aprovado e autenticado")
    public void approvePayment() {
        var providerPayment = new MercadoPagoGateway.ProviderPayment(
                "MP-BDD-1",
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                "approved",
                "accredited"
        );
        when(gateway.getPayment("MP-BDD-1")).thenReturn(providerPayment);
        when(persistenceService.applyProviderPayment(providerPayment))
                .thenAnswer(invocation -> {
                    payment.applyProviderStatus(
                            "MP-BDD-1",
                            "approved",
                            "accredited",
                            PaymentStatus.APPROVED,
                            NOW.plusSeconds(5)
                    );
                    return payment;
                });

        paymentService.handleWebhook(
                "MP-BDD-1",
                "payment",
                "request-bdd-1",
                "valid-signature"
        );
    }

    @Entao("existe somente um pagamento local")
    public void onePayment() {
        assertThat(duplicate).isSameAs(payment);
        verify(gateway, times(1)).createPreference(any());
    }

    @Entao("o pagamento fica com status APPROVED")
    public void paymentApproved() {
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(signatureValidator).validate(
                "MP-BDD-1",
                "request-bdd-1",
                "valid-signature"
        );
    }
}
