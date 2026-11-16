package br.com.fiap.mecanica.billing.payment.application;

import java.time.Clock;
import java.time.Instant;

import br.com.fiap.mecanica.billing.messaging.contract.EventContext;
import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import br.com.fiap.mecanica.billing.payment.infrastructure.PaymentRepository;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentPersistenceService {

    private final PaymentRepository repository;
    private final PaymentEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentPersistenceService(
            PaymentRepository repository,
            PaymentEventPublisher eventPublisher,
            Clock clock
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public Payment createOrGet(Quote quote) {
        return repository.findByQuoteId(quote.getId()).orElseGet(() -> {
            try {
                return repository.save(Payment.create(
                        quote.getId(),
                        quote.getOsId(),
                        quote.getTotal(),
                        quote.getCurrency(),
                        Instant.now(clock)
                ));
            } catch (DuplicateKeyException exception) {
                return repository.findByQuoteId(quote.getId())
                        .orElseThrow(() -> exception);
            }
        });
    }

    @Transactional
    public Payment attachPreference(
            String paymentId,
            MercadoPagoGateway.PreferenceResult preference
    ) {
        Payment payment = findById(paymentId);
        if (payment.getPreferenceId() != null) {
            return payment;
        }
        payment.attachPreference(
                preference.preferenceId(),
                preference.checkoutUrl(),
                Instant.now(clock)
        );
        Payment saved = repository.save(payment);
        eventPublisher.paymentPending(saved, EventContext.started(saved.getOsId()));
        return saved;
    }

    @Transactional
    public Payment markCreationFailed(String paymentId, String message) {
        Payment payment = findById(paymentId);
        if (payment.markError("MERCADO_PAGO_ERROR", message, Instant.now(clock))) {
            Payment saved = repository.save(payment);
            eventPublisher.paymentFailed(saved, EventContext.started(saved.getOsId()));
            return saved;
        }
        return payment;
    }

    @Transactional
    public Payment applyProviderPayment(MercadoPagoGateway.ProviderPayment providerPayment) {
        if (providerPayment.externalReference() == null
                || providerPayment.externalReference().isBlank()) {
            throw new InvalidPaymentStateException(
                    "Provider payment has no external reference"
            );
        }
        Payment payment = findById(providerPayment.externalReference());
        validateProviderPayment(payment, providerPayment);
        PaymentStatus newStatus = mapStatus(providerPayment.status());
        boolean changed = newStatus == PaymentStatus.ERROR
                ? payment.markError(
                        "UNKNOWN_PROVIDER_STATUS",
                        "Unsupported Mercado Pago status: " + providerPayment.status(),
                        Instant.now(clock)
                )
                : payment.applyProviderStatus(
                        providerPayment.externalPaymentId(),
                        providerPayment.status(),
                        providerPayment.statusDetail(),
                        newStatus,
                        Instant.now(clock)
                );
        if (!changed) {
            return payment;
        }

        Payment saved = repository.save(payment);
        publishStatus(saved);
        return saved;
    }

    public Payment findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private void validateProviderPayment(
            Payment payment,
            MercadoPagoGateway.ProviderPayment providerPayment
    ) {
        if (providerPayment.amount() == null
                || payment.getAmount().compareTo(providerPayment.amount()) != 0) {
            throw new InvalidPaymentStateException("Provider payment amount does not match");
        }
        if (!payment.getCurrency().equalsIgnoreCase(providerPayment.currency())) {
            throw new InvalidPaymentStateException("Provider payment currency does not match");
        }
    }

    private PaymentStatus mapStatus(String providerStatus) {
        if (providerStatus == null) {
            return PaymentStatus.ERROR;
        }
        return switch (providerStatus.toLowerCase()) {
            case "pending" -> PaymentStatus.PENDING;
            case "in_process", "in_mediation" -> PaymentStatus.IN_PROCESS;
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.ERROR;
        };
    }

    private void publishStatus(Payment payment) {
        EventContext context = EventContext.started(payment.getOsId());
        switch (payment.getStatus()) {
            case APPROVED -> eventPublisher.paymentApproved(payment, context);
            case REJECTED, CANCELLED -> eventPublisher.paymentRejected(payment, context);
            case ERROR -> eventPublisher.paymentFailed(payment, context);
            default -> {
                // Pending and in-process updates do not advance the Saga.
            }
        }
    }
}
