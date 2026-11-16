package br.com.fiap.mecanica.billing.shared.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.fiap.mecanica.billing.payment.application.InvalidPaymentStateException;
import br.com.fiap.mecanica.billing.payment.application.MercadoPagoException;
import br.com.fiap.mecanica.billing.payment.application.PaymentNotFoundException;
import br.com.fiap.mecanica.billing.payment.application.WebhookSignatureException;
import br.com.fiap.mecanica.billing.quote.application.QuoteAlreadyExistsException;
import br.com.fiap.mecanica.billing.quote.application.QuoteNotFoundException;
import br.com.fiap.mecanica.billing.quote.domain.InvalidQuoteStateException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({QuoteNotFoundException.class, PaymentNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(
            QuoteNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({
            QuoteAlreadyExistsException.class,
            InvalidQuoteStateException.class,
            InvalidPaymentStateException.class
    })
    public ResponseEntity<ApiError> handleConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> violations = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                violations.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, violations);
    }

    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<ApiError> handleUnauthorized(
            WebhookSignatureException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MercadoPagoException.class)
    public ResponseEntity<ApiError> handleProviderFailure(
            MercadoPagoException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request, Map.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> violations
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                violations
        ));
    }
}
