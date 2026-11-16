package br.com.fiap.mecanica.billing.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class QuoteTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant VALID_UNTIL = NOW.plusSeconds(3600);

    @Test
    void shouldCreateDraftAndCalculateTotal() {
        Quote quote = createQuote();

        assertThat(quote.getId()).isNull();
        assertThat(quote.getOsId()).isEqualTo("OS-1");
        assertThat(quote.getCustomerId()).isEqualTo("CUSTOMER-1");
        assertThat(quote.getItems()).hasSize(2);
        assertThat(quote.getTotal()).isEqualByComparingTo("125.50");
        assertThat(quote.getCurrency()).isEqualTo("BRL");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(quote.getValidUntil()).isEqualTo(VALID_UNTIL);
        assertThat(quote.getCreatedAt()).isEqualTo(NOW);
        assertThat(quote.getUpdatedAt()).isEqualTo(NOW);
        assertThat(quote.getSubmittedAt()).isNull();
        assertThat(quote.getDecidedAt()).isNull();
        assertThat(quote.getRejectionReason()).isNull();
        assertThat(quote.getDocumentVersion()).isNull();
    }

    @Test
    void shouldSubmitAndApproveQuoteIdempotently() {
        Quote quote = createQuote();
        Instant submittedAt = NOW.plusSeconds(10);
        Instant approvedAt = NOW.plusSeconds(20);

        quote.submit(submittedAt);
        quote.submit(submittedAt.plusSeconds(1));
        quote.approve(approvedAt);
        quote.approve(approvedAt.plusSeconds(1));

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(quote.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(quote.getDecidedAt()).isEqualTo(approvedAt);
        assertThat(quote.getUpdatedAt()).isEqualTo(approvedAt);
    }

    @Test
    void shouldRejectPendingQuoteIdempotently() {
        Quote quote = createQuote();
        quote.submit(NOW.plusSeconds(10));

        quote.reject("Customer declined", NOW.plusSeconds(20));
        quote.reject("Ignored duplicate", NOW.plusSeconds(30));

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.REJECTED);
        assertThat(quote.getRejectionReason()).isEqualTo("Customer declined");
        assertThat(quote.getDecidedAt()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    void shouldCancelOpenQuoteIdempotently() {
        Quote quote = createQuote();

        assertThat(quote.cancel("OS cancelled", NOW.plusSeconds(10))).isTrue();
        assertThat(quote.cancel("duplicate", NOW.plusSeconds(20))).isFalse();

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        assertThat(quote.getRejectionReason()).isEqualTo("OS cancelled");
        assertThat(quote.getDecidedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void shouldRejectCancellationOfClosedQuote() {
        Quote quote = createQuote();
        quote.submit(NOW.plusSeconds(1));
        quote.approve(NOW.plusSeconds(2));

        assertThatThrownBy(() -> quote.cancel("OS cancelled", NOW.plusSeconds(3)))
                .isInstanceOf(InvalidQuoteStateException.class);
    }

    @Test
    void shouldRejectInvalidTransitionsAndExpiredQuote() {
        Quote draft = createQuote();
        assertThatThrownBy(() -> draft.approve(NOW.plusSeconds(1)))
                .isInstanceOf(InvalidQuoteStateException.class);
        assertThatThrownBy(() -> draft.reject("No", NOW.plusSeconds(1)))
                .isInstanceOf(InvalidQuoteStateException.class);
        assertThatThrownBy(() -> draft.submit(VALID_UNTIL))
                .isInstanceOf(InvalidQuoteStateException.class)
                .hasMessage("Quote is expired");

        Quote pending = createQuote();
        pending.submit(NOW.plusSeconds(1));
        assertThatThrownBy(() -> pending.approve(VALID_UNTIL))
                .isInstanceOf(InvalidQuoteStateException.class)
                .hasMessage("Quote is expired");
        assertThatThrownBy(() -> pending.reject(" ", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidCreationData() {
        List<QuoteItem> items = List.of(item("Part", "10.00"));

        assertThatThrownBy(() -> Quote.create(
                " ", "CUSTOMER-1", items, "BRL", VALID_UNTIL, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quote.create(
                "OS-1", " ", items, "BRL", VALID_UNTIL, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quote.create(
                "OS-1", "CUSTOMER-1", List.of(), "BRL", VALID_UNTIL, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quote.create(
                "OS-1", "CUSTOMER-1", items, "REAL", VALID_UNTIL, NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quote.create(
                "OS-1", "CUSTOMER-1", items, "BRL", NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Quote createQuote() {
        return Quote.create(
                " OS-1 ",
                " CUSTOMER-1 ",
                List.of(
                        item("Part", "100.00"),
                        item("Labor", "25.50")
                ),
                "brl",
                VALID_UNTIL,
                NOW
        );
    }

    private QuoteItem item(String description, String price) {
        return new QuoteItem(
                description,
                QuoteItemType.PART,
                BigDecimal.ONE,
                new BigDecimal(price)
        );
    }
}
