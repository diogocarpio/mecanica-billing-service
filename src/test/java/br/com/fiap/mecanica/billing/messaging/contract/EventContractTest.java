package br.com.fiap.mecanica.billing.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EventContractTest {

    private static final Path EXAMPLES_DIR = Paths.get("docs/contracts/examples");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldValidateAllPublishedEventExamplesAgainstEnvelopeContract()
            throws IOException {
        assertThat(EXAMPLES_DIR).isDirectory();

        List<Path> examples;
        try (Stream<Path> files = Files.list(EXAMPLES_DIR)) {
            examples = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertThat(examples).isNotEmpty();

        for (Path example : examples) {
            JsonNode event = MAPPER.readTree(Files.readString(example));
            String fileName = example.getFileName().toString();

            assertThat(event.get("eventId").asText())
                    .as("eventId must be a UUID in %s", fileName)
                    .isNotBlank();
            UUID.fromString(event.get("eventId").asText());

            String eventType = event.get("eventType").asText();
            assertThat(eventType)
                    .as("eventType in %s", fileName)
                    .isNotBlank();
            String expectedType = fileName
                    .substring(0, fileName.length() - ".json".length())
                    .replace('-', '.');
            assertThat(eventType)
                    .as("eventType must match file name in %s", fileName)
                    .isEqualTo(expectedType);
            assertThat(eventType)
                    .as("eventType must be versioned in %s", fileName)
                    .endsWith(".v1");

            assertThat(event.get("eventVersion").asInt())
                    .as("eventVersion must be 1 in %s", fileName)
                    .isEqualTo(1);

            assertThat(event.get("occurredAt").asText())
                    .as("occurredAt must be an Instant in %s", fileName)
                    .isNotBlank();
            Instant.parse(event.get("occurredAt").asText());

            for (String field : List.of("source", "correlationId", "osId")) {
                assertThat(event.get(field).asText())
                        .as("%s must not be blank in %s", field, fileName)
                        .isNotBlank();
            }

            JsonNode payload = event.get("payload");
            assertThat(payload)
                    .as("payload must be an object in %s", fileName)
                    .isNotNull();
            assertThat(payload.isObject())
                    .as("payload must be an object in %s", fileName)
                    .isTrue();
        }
    }
}
