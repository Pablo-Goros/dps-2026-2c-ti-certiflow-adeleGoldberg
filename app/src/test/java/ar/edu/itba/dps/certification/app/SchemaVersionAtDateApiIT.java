package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2 over HTTP: "which version of the schema was in force on this date", including a version
 * that was scheduled for the future.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-schema-date-it;DB_CLOSE_DELAY=-1")
class SchemaVersionAtDateApiIT extends ApiTest {

    private static String schemaId;
    private static Instant switchOver;

    /** Version 1 in force since now; version 2 scheduled for a month from now. */
    @BeforeEach
    void publishTwoVersionsOnce() {
        if (schemaId != null) {
            return;
        }
        schemaId = api.post("/api/schemas", Map.of("name", "Laboratory", "assetTypes", List.of("LABORATORY")),
                null).text("id");
        api.post("/api/schemas/" + schemaId + "/draft", Map.of(), null);
        api.post("/api/schemas/" + schemaId + "/draft/sections", Fixtures.laboratorySection(), null);
        publish(Map.of());
        switchOver = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        api.post("/api/schemas/" + schemaId + "/draft", Map.of(), null); // starts as a copy of version 1
        publish(Map.of("effectiveFrom", switchOver.toString()));
    }

    private void publish(Map<String, Object> body) {
        var published = api.post("/api/schemas/" + schemaId + "/publish", body, null);
        assertThat(published.status()).as(published.body()).isEqualTo(201);
    }

    private Api.Reply versionAt(String instant) {
        return api.get("/api/schemas/" + schemaId + "/effective-version?at=" + instant);
    }

    @Test
    void withoutADateItIsTheVersionInForceNow() {
        var reply = api.get("/api/schemas/" + schemaId + "/effective-version");

        assertThat(reply.status()).as(reply.body()).isEqualTo(200);
        assertThat(reply.json().get("number")).isEqualTo(1);
    }

    @Test
    void beforeTheScheduledDateTheOldVersionStillRules() {
        var reply = versionAt(switchOver.minusSeconds(1).toString());

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("number")).isEqualTo(1);
    }

    @Test
    void fromTheScheduledDateTheNewVersionRules() {
        var exactly = versionAt(switchOver.toString());
        var later = versionAt(switchOver.plus(365, ChronoUnit.DAYS).toString());

        assertThat(exactly.json().get("number")).isEqualTo(2);
        assertThat(later.json().get("number")).isEqualTo(2);
    }

    @Test
    void beforeAnyVersionExistedThereWasNothingInForce() {
        var reply = versionAt("2000-01-01T00:00:00Z");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.text("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void theSchemaStillReportsTheVersionInForceToday() {
        var reply = api.get("/api/schemas/" + schemaId);

        assertThat(reply.json().get("effectiveVersion")).isEqualTo(1);
        assertThat((List<?>) reply.json().get("versions")).hasSize(2);
    }

    @Test
    void aDateThatIsNotAnInstantIsABadRequest() {
        var reply = versionAt("yesterday");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("INVALID_INPUT");
    }

    @Test
    void anUnknownSchemaIsNotFound() {
        var reply = api.get("/api/schemas/does-not-exist/effective-version?at=2030-01-01T00:00:00Z");

        assertThat(reply.status()).isEqualTo(404);
    }
}
