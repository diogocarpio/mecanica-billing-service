package br.com.fiap.mecanica.billing.quote.api;

import java.net.URI;

import br.com.fiap.mecanica.billing.quote.application.CreateQuoteCommand;
import br.com.fiap.mecanica.billing.quote.application.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "Quotes", description = "Work order quote lifecycle")
public class QuoteController {

    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a draft quote")
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody CreateQuoteRequest request) {
        var command = new CreateQuoteCommand(
                request.osId(),
                request.customerId(),
                request.items().stream()
                        .map(item -> new CreateQuoteCommand.Item(
                                item.description(),
                                item.type(),
                                item.quantity(),
                                item.unitPrice()
                        ))
                        .toList(),
                request.currency(),
                request.validUntil()
        );

        var response = QuoteResponse.from(service.create(command));
        return ResponseEntity.created(URI.create("/api/v1/quotes/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find a quote by id")
    public QuoteResponse findById(@PathVariable String id) {
        return QuoteResponse.from(service.findById(id));
    }

    @GetMapping(params = "osId")
    @Operation(summary = "Find the quote associated with a work order")
    public QuoteResponse findByOsId(@RequestParam String osId) {
        return QuoteResponse.from(service.findByOsId(osId));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a quote for approval")
    public QuoteResponse submit(@PathVariable String id) {
        return QuoteResponse.from(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending quote")
    public QuoteResponse approve(@PathVariable String id) {
        return QuoteResponse.from(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending quote")
    public QuoteResponse reject(
            @PathVariable String id,
            @Valid @RequestBody RejectQuoteRequest request
    ) {
        return QuoteResponse.from(service.reject(id, request.reason()));
    }
}
