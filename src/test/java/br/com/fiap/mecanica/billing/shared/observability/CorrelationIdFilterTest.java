package br.com.fiap.mecanica.billing.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void shouldPropagateIncomingCorrelationId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "corr-1");
        var response = new MockHttpServletResponse();
        var captured = new AtomicReference<String>();
        FilterChain chain = (req, res) -> captured.set(MDC.get("correlationId"));

        filter.doFilter(request, response, chain);

        assertThat(captured.get()).isEqualTo("corr-1");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("corr-1");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldGenerateCorrelationIdWhenAbsent() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    @Test
    void shouldRegisterMicrometerCounters() {
        var registry = new SimpleMeterRegistry();
        var metrics = new BillingMetrics(registry);

        metrics.eventEnqueued("billing.quote.created.v1");
        metrics.outboxPublish(true, "billing.quote.created.v1");
        metrics.outboxFailedPermanently("billing.payment.failed.v1");
        metrics.messageConsumed("os.quote.requested.v1", "processed");

        assertThat(registry.get("billing.events.enqueued").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("billing.outbox.publish").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("billing.outbox.failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("billing.messaging.consumed").counter().count()).isEqualTo(1.0);
    }
}
