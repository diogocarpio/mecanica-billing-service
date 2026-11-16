package br.com.fiap.mecanica.billing.quote.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("quotes")
public class Quote {

    @Id
    private String id;

    @Indexed(unique = true)
    private String osId;

    private String customerId;
    private List<QuoteItem> items;
    private BigDecimal total;
    private String currency;
    private QuoteStatus status;
    private Instant validUntil;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant decidedAt;
    private String rejectionReason;

    @Version
    private Long documentVersion;

    @PersistenceCreator
    public Quote(
            String id,
            String osId,
            String customerId,
            List<QuoteItem> items,
            BigDecimal total,
            String currency,
            QuoteStatus status,
            Instant validUntil,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt,
            Instant decidedAt,
            String rejectionReason,
            Long documentVersion
    ) {
        this.id = id;
        this.osId = osId;
        this.customerId = customerId;
        this.items = items == null ? List.of() : List.copyOf(items);
        this.total = total;
        this.currency = currency;
        this.status = status;
        this.validUntil = validUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.submittedAt = submittedAt;
        this.decidedAt = decidedAt;
        this.rejectionReason = rejectionReason;
        this.documentVersion = documentVersion;
    }

    public static Quote create(
            String osId,
            String customerId,
            List<QuoteItem> items,
            String currency,
            Instant validUntil,
            Instant now
    ) {
        String normalizedOsId = requireText(osId, "OS id is required");
        String normalizedCustomerId = requireText(customerId, "Customer id is required");
        List<QuoteItem> safeItems = requireItems(items);
        String normalizedCurrency = requireCurrency(currency);
        Instant creationTime = Objects.requireNonNull(now, "Creation time is required");
        Instant expirationTime = Objects.requireNonNull(validUntil, "Quote expiration is required");

        if (!expirationTime.isAfter(creationTime)) {
            throw new IllegalArgumentException("Quote expiration must be in the future");
        }

        BigDecimal total = safeItems.stream()
                .map(QuoteItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Quote(
                null,
                normalizedOsId,
                normalizedCustomerId,
                safeItems,
                total,
                normalizedCurrency,
                QuoteStatus.DRAFT,
                expirationTime,
                creationTime,
                creationTime,
                null,
                null,
                null,
                null
        );
    }

    public boolean submit(Instant now) {
        if (status == QuoteStatus.PENDING_APPROVAL) {
            return false;
        }
        ensureStatus(QuoteStatus.DRAFT, "Only a draft quote can be submitted");
        Instant transitionTime = Objects.requireNonNull(now, "Transition time is required");
        ensureNotExpired(transitionTime);
        status = QuoteStatus.PENDING_APPROVAL;
        submittedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    public boolean approve(Instant now) {
        if (status == QuoteStatus.APPROVED) {
            return false;
        }
        ensureStatus(QuoteStatus.PENDING_APPROVAL, "Only a pending quote can be approved");
        Instant transitionTime = Objects.requireNonNull(now, "Transition time is required");
        ensureNotExpired(transitionTime);
        status = QuoteStatus.APPROVED;
        decidedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    public boolean reject(String reason, Instant now) {
        if (status == QuoteStatus.REJECTED) {
            return false;
        }
        ensureStatus(QuoteStatus.PENDING_APPROVAL, "Only a pending quote can be rejected");
        rejectionReason = requireText(reason, "Rejection reason is required");
        Instant transitionTime = Objects.requireNonNull(now, "Transition time is required");
        status = QuoteStatus.REJECTED;
        decidedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    public boolean cancel(String reason, Instant now) {
        if (status == QuoteStatus.CANCELLED) {
            return false;
        }
        if (status != QuoteStatus.DRAFT && status != QuoteStatus.PENDING_APPROVAL) {
            throw new InvalidQuoteStateException("Only an open quote can be cancelled");
        }
        rejectionReason = requireText(reason, "Cancellation reason is required");
        Instant transitionTime = Objects.requireNonNull(now, "Transition time is required");
        status = QuoteStatus.CANCELLED;
        decidedAt = transitionTime;
        updatedAt = transitionTime;
        return true;
    }

    private void ensureNotExpired(Instant now) {
        if (!validUntil.isAfter(now)) {
            throw new InvalidQuoteStateException("Quote is expired");
        }
    }

    private void ensureStatus(QuoteStatus expected, String message) {
        if (status != expected) {
            throw new InvalidQuoteStateException(message);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String requireCurrency(String value) {
        String currency = requireText(value, "Currency is required").toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must use the ISO 4217 alpha-3 format");
        }
        return currency;
    }

    private static List<QuoteItem> requireItems(List<QuoteItem> items) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one valid quote item is required");
        }
        return List.copyOf(items);
    }

    public String getId() {
        return id;
    }

    public String getOsId() {
        return osId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<QuoteItem> getItems() {
        return List.copyOf(items);
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getDocumentVersion() {
        return documentVersion;
    }
}
