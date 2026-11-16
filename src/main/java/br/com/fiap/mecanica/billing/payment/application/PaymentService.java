package br.com.fiap.mecanica.billing.payment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import br.com.fiap.mecanica.billing.payment.infrastructure.PaymentRepository;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import br.com.fiap.mecanica.billing.quote.infrastructure.QuoteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final QuoteRepository quoteRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentPersistenceService persistenceService;
    private final MercadoPagoGateway mercadoPagoGateway;
    private final MercadoPagoWebhookSignatureValidator signatureValidator;
    private final Clock clock;
    private final Duration reconciliationAge;

    public PaymentService(
            QuoteRepository quoteRepository,
            PaymentRepository paymentRepository,
            PaymentPersistenceService persistenceService,
            MercadoPagoGateway mercadoPagoGateway,
            MercadoPagoWebhookSignatureValidator signatureValidator,
            Clock clock,
            @Value("${billing.mercado-pago.reconciliation-age:PT5M}")
            Duration reconciliationAge
    ) {
        this.quoteRepository = quoteRepository;
        this.paymentRepository = paymentRepository;
        this.persistenceService = persistenceService;
        this.mercadoPagoGateway = mercadoPagoGateway;
        this.signatureValidator = signatureValidator;
        this.clock = clock;
        this.reconciliationAge = reconciliationAge;
    }

    public Payment createPreference(String quoteId, String payerEmail) {
        var quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new InvalidPaymentStateException(
                        "Quote not found: " + quoteId
                ));
        if (quote.getStatus() != QuoteStatus.APPROVED) {
            throw new InvalidPaymentStateException(
                    "Payment requires an approved quote"
            );
        }

        Payment payment = persistenceService.createOrGet(quote);
        if (payment.getPreferenceId() != null || !payment.canRetryPreference()
                && payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }

        try {
            var preference = mercadoPagoGateway.createPreference(
                    new MercadoPagoGateway.PreferenceRequest(
                            payment.getId(),
                            payment.getQuoteId(),
                            payment.getOsId(),
                            payment.getAmount(),
                            payment.getCurrency(),
                            payerEmail,
                            payment.getIdempotencyKey()
                    )
            );
            return persistenceService.attachPreference(payment.getId(), preference);
        } catch (MercadoPagoException exception) {
            persistenceService.markCreationFailed(payment.getId(), exception.getMessage());
            throw exception;
        }
    }

    public Payment findById(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public Payment findByQuoteId(String quoteId) {
        return paymentRepository.findByQuoteId(quoteId)
                .orElseThrow(() -> new PaymentNotFoundException("quote " + quoteId));
    }

    public Payment findByOsId(String osId) {
        return paymentRepository.findByOsId(osId)
                .orElseThrow(() -> new PaymentNotFoundException("OS " + osId));
    }

    public boolean handleWebhook(
            String dataId,
            String type,
            String requestId,
            String signature
    ) {
        if (!"payment".equalsIgnoreCase(type)) {
            return false;
        }
        signatureValidator.validate(dataId, requestId, signature);
        var providerPayment = mercadoPagoGateway.getPayment(dataId);
        persistenceService.applyProviderPayment(providerPayment);
        return true;
    }

    public void reconcilePendingPayments() {
        Instant threshold = Instant.now(clock).minus(reconciliationAge);
        List<Payment> pending = paymentRepository
                .findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of(PaymentStatus.PENDING, PaymentStatus.IN_PROCESS),
                        threshold
                );
        pending.forEach(payment -> mercadoPagoGateway
                .findPaymentByExternalReference(payment.getId())
                .ifPresent(persistenceService::applyProviderPayment));
    }
}
