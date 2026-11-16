package br.com.fiap.mecanica.billing.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;

import br.com.fiap.mecanica.billing.payment.application.MercadoPagoException;
import br.com.fiap.mecanica.billing.payment.application.MercadoPagoGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MercadoPagoHttpGatewayTest {

    private MockRestServiceServer server;
    private MercadoPagoHttpGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MercadoPagoHttpGateway(
                builder,
                "https://api.mercadopago.test",
                "TEST-TOKEN",
                "https://billing.test/webhook",
                "https://client.test/success",
                "https://client.test/failure",
                "https://client.test/pending",
                true
        );
    }

    @Test
    void shouldCreateSandboxPreferenceWithIdempotency() {
        server.expect(requestTo("https://api.mercadopago.test/checkout/preferences"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer TEST-TOKEN"))
                .andExpect(header("X-Idempotency-Key", "IDEMPOTENCY-1"))
                .andExpect(content().string(containsString("\"external_reference\":\"PAY-1\"")))
                .andExpect(content().string(containsString("\"notification_url\"")))
                .andRespond(withSuccess("""
                        {
                          "id": "PREF-1",
                          "init_point": "https://production.test/1",
                          "sandbox_init_point": "https://sandbox.test/1"
                        }
                        """, APPLICATION_JSON));

        var result = gateway.createPreference(new MercadoPagoGateway.PreferenceRequest(
                "PAY-1",
                "QUOTE-1",
                "OS-1",
                new BigDecimal("100.00"),
                "BRL",
                "payer@testuser.com",
                "IDEMPOTENCY-1"
        ));

        assertThat(result.preferenceId()).isEqualTo("PREF-1");
        assertThat(result.checkoutUrl()).isEqualTo("https://sandbox.test/1");
        server.verify();
    }

    @Test
    void shouldQueryProviderPayment() {
        server.expect(requestTo("https://api.mercadopago.test/v1/payments/123"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "id": "123",
                          "external_reference": "PAY-1",
                          "transaction_amount": 100.00,
                          "currency_id": "BRL",
                          "status": "approved",
                          "status_detail": "accredited"
                        }
                        """, APPLICATION_JSON));

        var result = gateway.getPayment("123");

        assertThat(result.externalReference()).isEqualTo("PAY-1");
        assertThat(result.amount()).isEqualByComparingTo("100.00");
        assertThat(result.status()).isEqualTo("approved");
        server.verify();
    }

    @Test
    void shouldReturnEmptyReconciliationSearch() {
        server.expect(requestTo(containsString(
                        "https://api.mercadopago.test/v1/payments/search?"
                )))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"results\":[]}", APPLICATION_JSON));

        assertThat(gateway.findPaymentByExternalReference("PAY-1")).isEmpty();
        server.verify();
    }

    @Test
    void shouldRejectCallWithoutToken() {
        var noTokenGateway = new MercadoPagoHttpGateway(
                RestClient.builder(),
                "https://api.mercadopago.test",
                "",
                "",
                "",
                "",
                "",
                true
        );

        assertThatThrownBy(() -> noTokenGateway.getPayment("123"))
                .isInstanceOf(MercadoPagoException.class)
                .hasMessageContaining("not configured");
    }
}
