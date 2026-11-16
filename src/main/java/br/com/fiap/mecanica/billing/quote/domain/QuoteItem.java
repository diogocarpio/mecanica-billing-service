package br.com.fiap.mecanica.billing.quote.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record QuoteItem(
        String description,
        QuoteItemType type,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {

    public QuoteItem(String description, QuoteItemType type, BigDecimal quantity, BigDecimal unitPrice) {
        this(
                requireDescription(description),
                Objects.requireNonNull(type, "Item type is required"),
                requirePositive(quantity, "Item quantity must be greater than zero"),
                requireNonNegative(unitPrice, "Item unit price cannot be negative"),
                calculateSubtotal(quantity, unitPrice)
        );
    }

    public QuoteItem {
        description = requireDescription(description);
        type = Objects.requireNonNull(type, "Item type is required");
        quantity = requirePositive(quantity, "Item quantity must be greater than zero");
        unitPrice = requireNonNegative(unitPrice, "Item unit price cannot be negative");
        subtotal = requireNonNegative(subtotal, "Item subtotal cannot be negative")
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal calculateSubtotal(BigDecimal quantity, BigDecimal unitPrice) {
        return requirePositive(quantity, "Item quantity must be greater than zero")
                .multiply(requireNonNegative(unitPrice, "Item unit price cannot be negative"))
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    private static String requireDescription(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Item description is required");
        }
        return value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
