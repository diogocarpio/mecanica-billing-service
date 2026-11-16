package br.com.fiap.mecanica.billing.payment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class MercadoPagoWebhookSignatureValidatorTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final String SECRET = "sandbox-secret";

    @Test
    void shouldValidateAuthenticSignature() throws Exception {
        var validator = validator(SECRET);
        String dataId = "123456";
        String requestId = "request-1";
        String timestamp = String.valueOf(NOW.toEpochMilli());
        String hash = sign(
                "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";"
        );

        assertThatCode(() -> validator.validate(
                dataId,
                requestId,
                "ts=" + timestamp + ",v1=" + hash
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInvalidExpiredAndUnconfiguredSignatures() {
        var validator = validator(SECRET);

        assertThatThrownBy(() -> validator.validate(
                "123",
                "request-1",
                "ts=" + NOW.toEpochMilli() + ",v1=00"
        )).isInstanceOf(WebhookSignatureException.class);
        assertThatThrownBy(() -> validator.validate(
                "123",
                "request-1",
                "ts=" + NOW.minusSeconds(301).toEpochMilli() + ",v1=00"
        )).isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> validator("").validate("123", "request-1", "x"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("not configured");
    }

    private MercadoPagoWebhookSignatureValidator validator(String secret) {
        return new MercadoPagoWebhookSignatureValidator(
                secret,
                300,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private String sign(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(
                mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8))
        );
    }
}
