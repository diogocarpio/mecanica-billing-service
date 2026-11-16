package br.com.fiap.mecanica.billing.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class QuoteItemTest {

    @Test
    void shouldCalculateAndRoundSubtotal() {
        var item = new QuoteItem(
                "  Oil filter  ",
                QuoteItemType.PART,
                new BigDecimal("2.5"),
                new BigDecimal("10.235")
        );

        assertThat(item.description()).isEqualTo("Oil filter");
        assertThat(item.subtotal()).isEqualByComparingTo("25.59");
    }

    @Test
    void shouldRejectInvalidValues() {
        assertThatThrownBy(() -> new QuoteItem(
                " ",
                QuoteItemType.PART,
                BigDecimal.ONE,
                BigDecimal.ONE
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new QuoteItem(
                "Part",
                null,
                BigDecimal.ONE,
                BigDecimal.ONE
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new QuoteItem(
                "Part",
                QuoteItemType.PART,
                BigDecimal.ZERO,
                BigDecimal.ONE
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new QuoteItem(
                "Part",
                QuoteItemType.PART,
                BigDecimal.ONE,
                new BigDecimal("-0.01")
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new QuoteItem(
                "Part",
                QuoteItemType.PART,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("-0.01")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
