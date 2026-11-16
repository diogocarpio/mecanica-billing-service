package br.com.fiap.mecanica.billing.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.fiap.mecanica.billing.quote.application.QuoteAlreadyExistsException;
import br.com.fiap.mecanica.billing.quote.application.QuoteNotFoundException;
import br.com.fiap.mecanica.billing.quote.domain.InvalidQuoteStateException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/quotes/1");
    }

    @Test
    void shouldHandleNotFound() {
        var response = handler.handleNotFound(new QuoteNotFoundException("1"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/api/v1/quotes/1");
    }

    @Test
    void shouldHandleConflicts() {
        var duplicate = handler.handleConflict(new QuoteAlreadyExistsException("OS-1"), request);
        var invalidState = handler.handleConflict(new InvalidQuoteStateException("invalid"), request);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalidState.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldHandleBadRequest() {
        var response = handler.handleBadRequest(new IllegalArgumentException("invalid"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("invalid");
    }
}
