package br.com.fiap.mecanica.billing.shared.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.com.fiap.mecanica.billing.payment.api.PaymentController;
import br.com.fiap.mecanica.billing.payment.application.PaymentService;
import br.com.fiap.mecanica.billing.payment.application.WebhookSignatureException;
import br.com.fiap.mecanica.billing.payment.domain.Payment;
import br.com.fiap.mecanica.billing.payment.domain.PaymentStatus;
import br.com.fiap.mecanica.billing.quote.api.QuoteController;
import br.com.fiap.mecanica.billing.quote.application.QuoteNotFoundException;
import br.com.fiap.mecanica.billing.quote.application.QuoteService;
import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItem;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import br.com.fiap.mecanica.billing.shared.observability.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HttpContractTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final String CORRELATION_ID = "corr-contract-1";

    private final QuoteService quoteService = mock(QuoteService.class);
    private final PaymentService paymentService = mock(PaymentService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new QuoteController(quoteService),
                        new PaymentController(paymentService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @Test
    void shouldCreateQuoteAndReturnCreatedContract() throws Exception {
        Quote quote = quote("QUOTE-1", "OS-1");
        when(quoteService.create(any())).thenReturn(quote);

        mockMvc.perform(post("/api/v1/quotes")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/quotes/QUOTE-1"))
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.id").value("QUOTE-1"))
                .andExpect(jsonPath("$.osId").value("OS-1"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(165.50));
    }

    @Test
    void shouldRejectQuoteWithEmptyItemsUsingApiErrorContract() throws Exception {
        String body = """
                {
                  "osId": "OS-1",
                  "customerId": "CUSTOMER-1",
                  "items": [],
                  "currency": "BRL",
                  "validUntil": "2026-11-20T12:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/quotes")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/quotes"))
                .andExpect(jsonPath("$.violations.items").exists());
    }

    @Test
    void shouldReturnApiErrorWhenQuoteIsNotFound() throws Exception {
        when(quoteService.findById("UNKNOWN"))
                .thenThrow(new QuoteNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/quotes/UNKNOWN")
                        .header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/quotes/UNKNOWN"))
                .andExpect(jsonPath("$.violations").isMap());
    }

    @Test
    void shouldCreatePaymentPreferenceAndReturnCreatedContract() throws Exception {
        Payment payment = payment();
        when(paymentService.createPreference("QUOTE-1", "buyer@test.com"))
                .thenReturn(payment);

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quoteId": "QUOTE-1",
                                  "payerEmail": "buyer@test.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/payments/PAY-1"))
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.id").value("PAY-1"))
                .andExpect(jsonPath("$.quoteId").value("QUOTE-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://mp.test/checkout/PAY-1"));
    }

    @Test
    void shouldRejectPaymentWithInvalidEmailUsingApiErrorContract() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quoteId": "QUOTE-1",
                                  "payerEmail": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"))
                .andExpect(jsonPath("$.violations.payerEmail").exists());
    }

    @Test
    void shouldRejectInvalidWebhookSignatureUsingApiErrorContract() throws Exception {
        when(paymentService.handleWebhook("MP-1", "payment", "req-1", "bad-sig"))
                .thenThrow(new WebhookSignatureException("Invalid webhook signature"));

        mockMvc.perform(post("/api/v1/payments/webhooks/mercado-pago")
                        .param("data.id", "MP-1")
                        .param("type", "payment")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .header("x-request-id", "req-1")
                        .header("x-signature", "bad-sig"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid webhook signature"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/payments/webhooks/mercado-pago"));
    }

    @Test
    void shouldFindPaymentByIdQuoteAndOs() throws Exception {
        Payment payment = payment();
        when(paymentService.findById("PAY-1")).thenReturn(payment);
        when(paymentService.findByQuoteId("QUOTE-1")).thenReturn(payment);
        when(paymentService.findByOsId("OS-1")).thenReturn(payment);

        mockMvc.perform(get("/api/v1/payments/PAY-1")
                        .header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PAY-1"));
        mockMvc.perform(get("/api/v1/payments")
                        .param("quoteId", "QUOTE-1")
                        .header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PAY-1"));
        mockMvc.perform(get("/api/v1/payments")
                        .param("osId", "OS-1")
                        .header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PAY-1"));
    }

    @Test
    void shouldAcceptValidWebhook() throws Exception {
        when(paymentService.handleWebhook("MP-1", "payment", "req-1", "sig"))
                .thenReturn(true);

        mockMvc.perform(post("/api/v1/payments/webhooks/mercado-pago")
                        .param("data.id", "MP-1")
                        .param("type", "payment")
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .header("x-request-id", "req-1")
                        .header("x-signature", "sig"))
                .andExpect(status().isNoContent());
    }

    private String validQuoteJson() {
        return """
                {
                  "osId": "OS-1",
                  "customerId": "CUSTOMER-1",
                  "items": [
                    {
                      "description": "Labor",
                      "type": "LABOR",
                      "quantity": 1,
                      "unitPrice": 120.00
                    },
                    {
                      "description": "Oil filter",
                      "type": "PART",
                      "quantity": 1,
                      "unitPrice": 45.50
                    }
                  ],
                  "currency": "BRL",
                  "validUntil": "2026-11-20T12:00:00Z"
                }
                """;
    }

    private Quote quote(String id, String osId) {
        return new Quote(
                id,
                osId,
                "CUSTOMER-1",
                List.of(
                        new QuoteItem("Labor", QuoteItemType.LABOR,
                                BigDecimal.ONE, new BigDecimal("120.00")),
                        new QuoteItem("Oil filter", QuoteItemType.PART,
                                BigDecimal.ONE, new BigDecimal("45.50"))
                ),
                new BigDecimal("165.50"),
                "BRL",
                QuoteStatus.DRAFT,
                NOW.plusSeconds(432000),
                NOW,
                NOW,
                null,
                null,
                null,
                0L
        );
    }

    private Payment payment() {
        return new Payment(
                "PAY-1",
                "QUOTE-1",
                "OS-1",
                new BigDecimal("165.50"),
                "BRL",
                PaymentStatus.PENDING,
                "IDEM-1",
                "PREF-1",
                "https://mp.test/checkout/PAY-1",
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                null,
                0L
        );
    }
}
