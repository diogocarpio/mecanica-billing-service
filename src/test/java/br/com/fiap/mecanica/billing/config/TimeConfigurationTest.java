package br.com.fiap.mecanica.billing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TimeConfigurationTest {

    @Test
    void shouldProvideUtcClock() {
        Clock clock = new TimeConfiguration().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
