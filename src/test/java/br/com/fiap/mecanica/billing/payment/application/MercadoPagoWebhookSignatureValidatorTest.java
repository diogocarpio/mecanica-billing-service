package br.com.fiap.mecanica.billing.payment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
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

    @Test
    void shouldRejectIncompleteSignatureData() {
        var validator = validator(SECRET);

        assertThatThrownBy(() -> validator.validate(null, "req", "sig"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() -> validator.validate(" ", "req", "sig"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() -> validator.validate("123", null, "sig"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() -> validator.validate("123", " ", "sig"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() -> validator.validate("123", "req", null))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() -> validator.validate("123", "req", " "))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void shouldRejectNullSecret() {
        assertThatThrownBy(() -> validator(null).validate("123", "req", "sig"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldRejectMalformedSignatureFormat() {
        var validator = validator(SECRET);

        assertThatThrownBy(() -> validator.validate("123", "req", "ts=123"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("format is invalid");
        assertThatThrownBy(() -> validator.validate("123", "req", "v1=abc"))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("format is invalid");
        assertThatThrownBy(() -> validator.validate("123", "req", "junk,ts=123,v1=abc"))
                .isInstanceOf(WebhookSignatureException.class);
    }

    @Test
    void shouldRejectInvalidTimestampAndHash() {
        var validator = validator(SECRET);

        assertThatThrownBy(() -> validator.validate(
                "123", "req", "ts=abc,v1=00"
        )).isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("timestamp is invalid");
        assertThatThrownBy(() -> validator.validate(
                "123", "req", "ts=" + NOW.toEpochMilli() + ",v1=zz"
        )).isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("hash is invalid");
    }

    @Test
    void shouldAcceptSignatureWithSecondsTimestamp() throws Exception {
        var validator = validator(SECRET);
        String dataId = "123456";
        String requestId = "request-1";
        String timestamp = String.valueOf(NOW.getEpochSecond());
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
    void shouldWrapCryptoFailuresAsIllegalState() {
        var validator = validator(SECRET);
        String dataId = "123456";
        String requestId = "request-1";
        String timestamp = String.valueOf(NOW.toEpochMilli());
        String signature = "ts=" + timestamp + ",v1=00";

        try (var macStatic = mockStatic(Mac.class)) {
            macStatic.when(() -> Mac.getInstance("HmacSHA256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> validator.validate(dataId, requestId, signature))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not validate webhook signature");
        }
    }

    @Test
    void shouldWrapKeyFailuresAsIllegalState() throws Exception {
        var validator = validator(SECRET);
        String dataId = "123456";
        String requestId = "request-1";
        String timestamp = String.valueOf(NOW.toEpochMilli());
        String signature = "ts=" + timestamp + ",v1=00";
        Mac mac = mock(Mac.class);
        doThrow(new InvalidKeyException("bad key")).when(mac).init(any(Key.class));

        try (var macStatic = mockStatic(Mac.class)) {
            macStatic.when(() -> Mac.getInstance("HmacSHA256")).thenReturn(mac);

            assertThatThrownBy(() -> validator.validate(dataId, requestId, signature))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not validate webhook signature");
        }
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
