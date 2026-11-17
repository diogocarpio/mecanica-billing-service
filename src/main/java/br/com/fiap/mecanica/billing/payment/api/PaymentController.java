package br.com.fiap.mecanica.billing.payment.api;

import java.net.URI;

import br.com.fiap.mecanica.billing.payment.application.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Mercado Pago checkout and payment confirmation")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create an idempotent Mercado Pago checkout preference")
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse response = PaymentResponse.from(
                service.createPreference(request.quoteId(), request.payerEmail())
        );
        return ResponseEntity
                .created(URI.create("/api/v1/payments/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a payment by id")
    public PaymentResponse findById(@PathVariable String id) {
        return PaymentResponse.from(service.findById(id));
    }

    @GetMapping(params = "quoteId")
    @Operation(summary = "Find a payment by quote")
    public PaymentResponse findByQuoteId(@RequestParam String quoteId) {
        return PaymentResponse.from(service.findByQuoteId(quoteId));
    }

    @GetMapping(params = "osId")
    @Operation(summary = "Find a payment by work order")
    public PaymentResponse findByOsId(@RequestParam String osId) {
        return PaymentResponse.from(service.findByOsId(osId));
    }

    @PostMapping("/webhooks/mercado-pago")
    @Operation(summary = "Receive an authenticated Mercado Pago webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(name = "data.id") String dataId,
            @RequestParam String type,
            @RequestHeader("x-request-id") String requestId,
            @RequestHeader("x-signature") String signature
    ) {
        service.handleWebhook(dataId, type, requestId, signature);
        return ResponseEntity.noContent().build();
    }
}
