package br.com.fiap.mecanica.billing.quote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import br.com.fiap.mecanica.billing.quote.domain.Quote;
import br.com.fiap.mecanica.billing.quote.domain.QuoteItemType;
import br.com.fiap.mecanica.billing.quote.domain.QuoteStatus;
import br.com.fiap.mecanica.billing.quote.infrastructure.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    @Mock
    private QuoteRepository repository;

    @Mock
    private QuoteEventPublisher eventPublisher;

    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher
        );
    }

    @Test
    void shouldCreateQuote() {
        CreateQuoteCommand command = command();
        when(repository.existsByOsId("OS-1")).thenReturn(false);
        when(repository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Quote result = service.create(command);

        assertThat(result.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(result.getTotal()).isEqualByComparingTo("50.00");
        verify(repository).save(result);
        verify(eventPublisher).quoteCreated(
                org.mockito.ArgumentMatchers.eq(result),
                any()
        );
    }

    @Test
    void shouldRejectExistingQuoteBeforeSave() {
        when(repository.existsByOsId("OS-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(QuoteAlreadyExistsException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldTranslateDuplicateKeyRace() {
        when(repository.existsByOsId("OS-1")).thenReturn(false);
        doThrow(new DuplicateKeyException("duplicate")).when(repository).save(any(Quote.class));

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(QuoteAlreadyExistsException.class);
    }

    @Test
    void shouldFindByIdAndOsId() {
        Quote quote = quote();
        when(repository.findById("QUOTE-1")).thenReturn(Optional.of(quote));
        when(repository.findByOsId("OS-1")).thenReturn(Optional.of(quote));

        assertThat(service.findById("QUOTE-1")).isSameAs(quote);
        assertThat(service.findByOsId("OS-1")).isSameAs(quote);
    }

    @Test
    void shouldReportMissingQuote() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(repository.findByOsId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOf(QuoteNotFoundException.class);
        assertThatThrownBy(() -> service.findByOsId("missing"))
                .isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void shouldExecuteQuoteTransitions() {
        Quote quote = quote();
        when(repository.findById("QUOTE-1")).thenReturn(Optional.of(quote));
        when(repository.save(quote)).thenReturn(quote);

        assertThat(service.submit("QUOTE-1").getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        assertThat(service.approve("QUOTE-1").getStatus()).isEqualTo(QuoteStatus.APPROVED);
        verify(repository, times(2)).save(quote);
        verify(eventPublisher).quoteSubmitted(
                org.mockito.ArgumentMatchers.eq(quote),
                any()
        );
        verify(eventPublisher).quoteApproved(
                org.mockito.ArgumentMatchers.eq(quote),
                any()
        );

        Quote rejected = quote();
        rejected.submit(NOW);
        when(repository.findById("QUOTE-2")).thenReturn(Optional.of(rejected));
        when(repository.save(rejected)).thenReturn(rejected);

        assertThat(service.reject("QUOTE-2", "No").getStatus()).isEqualTo(QuoteStatus.REJECTED);
        verify(eventPublisher).quoteRejected(
                org.mockito.ArgumentMatchers.eq(rejected),
                any()
        );
    }

    @Test
    void shouldCancelOpenQuoteAndAvoidDuplicateEffects() {
        Quote quote = quote();
        when(repository.findByOsId("OS-1")).thenReturn(Optional.of(quote));
        when(repository.save(quote)).thenReturn(quote);

        assertThat(service.cancelByOsId("OS-1", "OS cancelled", context()).getStatus())
                .isEqualTo(QuoteStatus.CANCELLED);
        assertThat(service.cancelByOsId("OS-1", "duplicate", context()).getStatus())
                .isEqualTo(QuoteStatus.CANCELLED);

        verify(repository).save(quote);
        verify(eventPublisher).quoteCancelled(
                org.mockito.ArgumentMatchers.eq(quote),
                any()
        );
    }

    @Test
    void shouldNotPublishDuplicateTransitionEvents() {
        Quote quote = quote();
        quote.submit(NOW.minusSeconds(1));
        when(repository.findById("QUOTE-1")).thenReturn(Optional.of(quote));

        assertThat(service.submit("QUOTE-1").getStatus())
                .isEqualTo(QuoteStatus.PENDING_APPROVAL);

        verify(repository, never()).save(quote);
        verify(eventPublisher, never()).quoteSubmitted(any(), any());
    }

    private CreateQuoteCommand command() {
        return new CreateQuoteCommand(
                "OS-1",
                "CUSTOMER-1",
                List.of(new CreateQuoteCommand.Item(
                        "Labor",
                        QuoteItemType.LABOR,
                        new BigDecimal("2"),
                        new BigDecimal("25.00")
                )),
                "BRL",
                NOW.plusSeconds(3600)
        );
    }

    private Quote quote() {
        return Quote.create(
                "OS-1",
                "CUSTOMER-1",
                List.of(new br.com.fiap.mecanica.billing.quote.domain.QuoteItem(
                        "Labor",
                        QuoteItemType.LABOR,
                        BigDecimal.ONE,
                        new BigDecimal("50.00")
                )),
                "BRL",
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60)
        );
    }

    private br.com.fiap.mecanica.billing.messaging.contract.EventContext context() {
        return br.com.fiap.mecanica.billing.messaging.contract.EventContext.started("OS-1");
    }
}
