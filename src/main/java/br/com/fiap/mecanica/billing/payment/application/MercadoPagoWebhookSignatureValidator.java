package br.com.fiap.mecanica.billing.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoWebhookSignatureValidator {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final String secret;
    private final long toleranceSeconds;
    private final Clock clock;

    public MercadoPagoWebhookSignatureValidator(
            @Value("${billing.mercado-pago.webhook-secret:}") String secret,
            @Value("${billing.mercado-pago.webhook-tolerance-seconds:300}")
            long toleranceSeconds,
            Clock clock
    ) {
        this.secret = secret;
        this.toleranceSeconds = toleranceSeconds;
        this.clock = clock;
    }

    public void validate(String dataId, String requestId, String signature) {
        if (secret == null || secret.isBlank()) {
            throw new WebhookSignatureException("Mercado Pago webhook secret is not configured");
        }
        if (dataId == null || dataId.isBlank()
                || requestId == null || requestId.isBlank()
                || signature == null || signature.isBlank()) {
            throw new WebhookSignatureException("Webhook signature data is incomplete");
        }

        Map<String, String> parts = new HashMap<>();
        Arrays.stream(signature.split(","))
                .map(String::trim)
                .map(value -> value.split("=", 2))
                .filter(value -> value.length == 2)
                .forEach(value -> parts.put(value[0], value[1]));
        String timestamp = parts.get("ts");
        String receivedHash = parts.get("v1");
        if (timestamp == null || receivedHash == null) {
            throw new WebhookSignatureException("Webhook signature format is invalid");
        }
        validateTimestamp(timestamp);

        String manifest = "id:" + dataId.toLowerCase(Locale.ROOT)
                + ";request-id:" + requestId
                + ";ts:" + timestamp + ";";
        byte[] expectedHash = hmac(manifest);
        byte[] providedHash;
        try {
            providedHash = HexFormat.of().parseHex(receivedHash);
        } catch (IllegalArgumentException exception) {
            throw new WebhookSignatureException("Webhook signature hash is invalid");
        }
        if (!MessageDigest.isEqual(expectedHash, providedHash)) {
            throw new WebhookSignatureException("Webhook signature is invalid");
        }
    }

    private void validateTimestamp(String timestamp) {
        try {
            long rawTimestamp = Long.parseLong(timestamp);
            Instant signedAt = timestamp.length() <= 10
                    ? Instant.ofEpochSecond(rawTimestamp)
                    : Instant.ofEpochMilli(rawTimestamp);
            long age = Math.abs(Instant.now(clock).getEpochSecond() - signedAt.getEpochSecond());
            if (age > toleranceSeconds) {
                throw new WebhookSignatureException("Webhook signature has expired");
            }
        } catch (NumberFormatException exception) {
            throw new WebhookSignatureException("Webhook timestamp is invalid");
        }
    }

    private byte[] hmac(String manifest) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not validate webhook signature", exception);
        }
    }
}
