package br.com.fiap.mecanica.billing.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("payments")
public class Payment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String quoteId;

    @Indexed
    private String osId;

    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;

    @Indexed(unique = true)
    private String idempotencyKey;

    private String preferenceId;
    private String checkoutUrl;

    @Indexed(unique = true, sparse = true)
    private String externalPaymentId;

    private String providerStatus;
    private String providerStatusDetail;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant approvedAt;

    @Version
    private Long documentVersion;

    @PersistenceCreator
    public Payment(
            String id,
            String quoteId,
            String osId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String idempotencyKey,
            String preferenceId,
            String checkoutUrl,
            String externalPaymentId,
            String providerStatus,
            String providerStatusDetail,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant approvedAt,
            Long documentVersion
    ) {
        this.id = id;
        this.quoteId = quoteId;
        this.osId = osId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.preferenceId = preferenceId;
        this.checkoutUrl = checkoutUrl;
        this.externalPaymentId = externalPaymentId;
        this.providerStatus = providerStatus;
        this.providerStatusDetail = providerStatusDetail;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.approvedAt = approvedAt;
        this.documentVersion = documentVersion;
    }

    public static Payment create(
            String quoteId,
            String osId,
            BigDecimal amount,
            String currency,
            Instant now
    ) {
        String normalizedQuoteId = requireText(quoteId, "Quote id is required");
        String normalizedOsId = requireText(osId, "OS id is required");
        BigDecimal safeAmount = Objects.requireNonNull(amount, "Payment amount is required");
        if (safeAmount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        String normalizedCurrency = requireText(currency, "Currency is required")
                .toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must have three letters");
        }
        Instant creationTime = Objects.requireNonNull(now, "Creation time is required");

        return new Payment(
                UUID.randomUUID().toString(),
                normalizedQuoteId,
                normalizedOsId,
                safeAmount,
                normalizedCurrency,
                PaymentStatus.PENDING,
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                creationTime,
                creationTime,
                null,
                null
        );
    }

    public void attachPreference(String preferenceId, String checkoutUrl, Instant now) {
        this.preferenceId = requireText(preferenceId, "Preference id is required");
        this.checkoutUrl = requireText(checkoutUrl, "Checkout URL is required");
        status = PaymentStatus.PENDING;
        errorCode = null;
        errorMessage = null;
        updatedAt = Objects.requireNonNull(now, "Update time is required");
    }

    public boolean applyProviderStatus(
            String externalPaymentId,
            String providerStatus,
            String providerStatusDetail,
            PaymentStatus newStatus,
            Instant now
    ) {
        Objects.requireNonNull(newStatus, "Payment status is required");
        if (isTerminal(status)) {
            return false;
        }

        this.externalPaymentId = requireText(
                externalPaymentId,
                "External payment id is required"
        );
        this.providerStatus = requireText(providerStatus, "Provider status is required");
        this.providerStatusDetail = providerStatusDetail;
        status = newStatus;
        updatedAt = Objects.requireNonNull(now, "Update time is required");
        errorCode = null;
        errorMessage = null;
        if (newStatus == PaymentStatus.APPROVED) {
            approvedAt = updatedAt;
        }
        return true;
    }

    public boolean markError(String code, String message, Instant now) {
        if (isTerminal(status)) {
            return false;
        }
        String safeCode = requireText(code, "Error code is required");
        String safeMessage = requireText(message, "Error message is required");
        if (status == PaymentStatus.ERROR
                && safeCode.equals(errorCode)
                && safeMessage.equals(errorMessage)) {
            return false;
        }
        status = PaymentStatus.ERROR;
        errorCode = safeCode;
        errorMessage = safeMessage;
        updatedAt = Objects.requireNonNull(now, "Update time is required");
        return true;
    }

    public boolean canRetryPreference() {
        return status == PaymentStatus.ERROR && preferenceId == null;
    }

    private boolean isTerminal(PaymentStatus value) {
        return value == PaymentStatus.APPROVED
                || value == PaymentStatus.REJECTED
                || value == PaymentStatus.CANCELLED;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public String getOsId() {
        return osId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPreferenceId() {
        return preferenceId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public String getExternalPaymentId() {
        return externalPaymentId;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getProviderStatusDetail() {
        return providerStatusDetail;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Long getDocumentVersion() {
        return documentVersion;
    }
}
