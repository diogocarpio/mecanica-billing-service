package br.com.fiap.mecanica.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class BillingServiceApplicationTest {

    @Test
    void shouldDelegateToSpringApplication() {
        try (var springApplication = mockStatic(SpringApplication.class)) {
            BillingServiceApplication.main(new String[]{"--server.port=0"});

            springApplication.verify(() -> SpringApplication.run(
                    eq(BillingServiceApplication.class),
                    any(String[].class)
            ));
        }
    }

    @Test
    void shouldBeInstantiable() {
        assertThat(new BillingServiceApplication()).isNotNull();
    }
}
