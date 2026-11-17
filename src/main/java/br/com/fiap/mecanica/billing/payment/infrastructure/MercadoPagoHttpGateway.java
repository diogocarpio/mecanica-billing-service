package br.com.fiap.mecanica.billing.payment.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.fiap.mecanica.billing.payment.application.MercadoPagoException;
import br.com.fiap.mecanica.billing.payment.application.MercadoPagoGateway;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MercadoPagoHttpGateway implements MercadoPagoGateway {

    private final RestClient restClient;
    private final String accessToken;
    private final String notificationUrl;
    private final String successUrl;
    private final String failureUrl;
    private final String pendingUrl;
    private final boolean sandbox;

    public MercadoPagoHttpGateway(
            RestClient.Builder restClientBuilder,
            @Value("${billing.mercado-pago.base-url:https://api.mercadopago.com}")
            String baseUrl,
            @Value("${billing.mercado-pago.access-token:}") String accessToken,
            @Value("${billing.mercado-pago.notification-url:}") String notificationUrl,
            @Value("${billing.mercado-pago.back-urls.success:}") String successUrl,
            @Value("${billing.mercado-pago.back-urls.failure:}") String failureUrl,
            @Value("${billing.mercado-pago.back-urls.pending:}") String pendingUrl,
            @Value("${billing.mercado-pago.sandbox:true}") boolean sandbox
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
        this.successUrl = successUrl;
        this.failureUrl = failureUrl;
        this.pendingUrl = pendingUrl;
        this.sandbox = sandbox;
    }

    @Override
    public PreferenceResult createPreference(PreferenceRequest request) {
        requireCredentials();
        try {
            PreferenceResponse response = restClient.post()
                    .uri("/checkout/preferences")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("X-Idempotency-Key", request.idempotencyKey())
                    .body(preferenceBody(request))
                    .retrieve()
                    .body(PreferenceResponse.class);
            if (response == null || response.id() == null) {
                throw new MercadoPagoException("Mercado Pago returned an empty preference");
            }
            String checkoutUrl = sandbox && response.sandboxInitPoint() != null
                    ? response.sandboxInitPoint()
                    : response.initPoint();
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                throw new MercadoPagoException("Mercado Pago returned no checkout URL");
            }
            return new PreferenceResult(response.id(), checkoutUrl);
        } catch (MercadoPagoException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MercadoPagoException("Could not create Mercado Pago preference", exception);
        }
    }

    @Override
    public ProviderPayment getPayment(String externalPaymentId) {
        requireCredentials();
        try {
            PaymentResponse response = restClient.get()
                    .uri("/v1/payments/{id}", externalPaymentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(PaymentResponse.class);
            if (response == null) {
                throw new MercadoPagoException("Mercado Pago returned an empty payment");
            }
            return response.toProviderPayment();
        } catch (MercadoPagoException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MercadoPagoException("Could not query Mercado Pago payment", exception);
        }
    }

    @Override
    public Optional<ProviderPayment> findPaymentByExternalReference(String paymentId) {
        requireCredentials();
        try {
            SearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/payments/search")
                            .queryParam("external_reference", paymentId)
                            .queryParam("sort", "date_created")
                            .queryParam("criteria", "desc")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(SearchResponse.class);
            if (response == null || response.results().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.results().get(0).toProviderPayment());
        } catch (RestClientException exception) {
            throw new MercadoPagoException("Could not reconcile Mercado Pago payment", exception);
        }
    }

    private Map<String, Object> preferenceBody(PreferenceRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("external_reference", request.paymentId());
        body.put("items", List.of(Map.of(
                "id", request.quoteId(),
                "title", "Ordem de servico " + request.osId(),
                "description", "Pagamento do orcamento " + request.quoteId(),
                "quantity", 1,
                "currency_id", request.currency(),
                "unit_price", request.amount()
        )));
        if (request.payerEmail() != null && !request.payerEmail().isBlank()) {
            body.put("payer", Map.of("email", request.payerEmail()));
        }
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }
        Map<String, String> backUrls = backUrls();
        if (!backUrls.isEmpty()) {
            body.put("back_urls", backUrls);
            if (backUrls.containsKey("success")) {
                body.put("auto_return", "approved");
            }
        }
        return body;
    }

    private Map<String, String> backUrls() {
        Map<String, String> urls = new LinkedHashMap<>();
        addIfPresent(urls, "success", successUrl);
        addIfPresent(urls, "failure", failureUrl);
        addIfPresent(urls, "pending", pendingUrl);
        return urls;
    }

    private void addIfPresent(Map<String, String> urls, String key, String value) {
        if (value != null && !value.isBlank()) {
            urls.put(key, value);
        }
    }

    private void requireCredentials() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new MercadoPagoException("Mercado Pago access token is not configured");
        }
    }

    private record PreferenceResponse(
            String id,
            @JsonProperty("init_point") String initPoint,
            @JsonProperty("sandbox_init_point") String sandboxInitPoint
    ) {
    }

    private record SearchResponse(List<PaymentResponse> results) {
        private SearchResponse {
            results = results == null ? new ArrayList<>() : results;
        }
    }

    private record PaymentResponse(
            String id,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("transaction_amount") java.math.BigDecimal amount,
            @JsonProperty("currency_id") String currency,
            String status,
            @JsonProperty("status_detail") String statusDetail
    ) {
        ProviderPayment toProviderPayment() {
            return new ProviderPayment(
                    id,
                    externalReference,
                    amount,
                    currency,
                    status,
                    statusDetail
            );
        }
    }
}
