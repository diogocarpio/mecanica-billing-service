package br.com.fiap.mecanica.billing.quote.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.fiap.mecanica.billing.quote.application.CreateQuoteCommand;
import br.com.fiap.mecanica.billing.quote.application.QuoteService;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private QuoteService service;

    private QuoteController controller;
    private Quote quote;

    @BeforeEach
    void setUp() {
        controller = new QuoteController(service);
        quote = storedQuote();
    }

    @Test
    void shouldCreateQuote() {
        var request = new CreateQuoteRequest(
                "OS-1",
                "CUSTOMER-1",
                List.of(new QuoteItemRequest(
                        "Part",
                        QuoteItemType.PART,
                        BigDecimal.ONE,
                        new BigDecimal("50.00")
                )),
                "BRL",
                NOW.plusSeconds(3600)
        );
        when(service.create(any(CreateQuoteCommand.class))).thenReturn(quote);

        var response = controller.create(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/quotes/QUOTE-1");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldExposeAllQuoteOperations() {
        when(service.findById("QUOTE-1")).thenReturn(quote);
        when(service.findByOsId("OS-1")).thenReturn(quote);
        when(service.submit("QUOTE-1")).thenReturn(quote);
        when(service.approve("QUOTE-1")).thenReturn(quote);
        when(service.reject("QUOTE-1", "No")).thenReturn(quote);

        assertThat(controller.findById("QUOTE-1").id()).isEqualTo("QUOTE-1");
        assertThat(controller.findByOsId("OS-1").osId()).isEqualTo("OS-1");
        assertThat(controller.submit("QUOTE-1").status()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(controller.approve("QUOTE-1").id()).isEqualTo("QUOTE-1");
        assertThat(controller.reject("QUOTE-1", new RejectQuoteRequest("No")).id())
                .isEqualTo("QUOTE-1");
    }

    private Quote storedQuote() {
        return new Quote(
                "QUOTE-1",
                "OS-1",
                "CUSTOMER-1",
                List.of(new QuoteItem(
                        "Part",
                        QuoteItemType.PART,
                        BigDecimal.ONE,
                        new BigDecimal("50.00")
                )),
                new BigDecimal("50.00"),
                "BRL",
                QuoteStatus.DRAFT,
                NOW.plusSeconds(3600),
                NOW,
                NOW,
                null,
                null,
                null,
                0L
        );
    }
}
