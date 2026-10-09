package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-schema-it;DB_CLOSE_DELAY=-1")
class SchemaApiIT extends ApiTest {

    private String createSchema(String name, String... assetTypes) {
        var reply = api.post("/api/schemas", Map.of("name", name, "assetTypes", List.of(assetTypes)), null);
        assertThat(reply.status()).as(reply.body()).isEqualTo(201);
        return reply.text("id");
    }

    @Test
    void aSchemaGoesFromDraftToPublishedVersion() {
        String id = createSchema("Equipment inspection", "EQUIPMENT");

        var opened = api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        var badSection = Fixtures.laboratorySection();
        var badCriteria = new java.util.ArrayList<Map<String, Object>>();
        badCriteria.add(Map.of("id", "X", "rule", Map.of("type", "MAGIC"), "evidence", List.of()));
        badSection = Map.of("name", "Bad", "order", 9, "criteria", badCriteria);
        var rejected = api.post("/api/schemas/" + id + "/draft/sections", badSection, null);
        var withSection = api.post("/api/schemas/" + id + "/draft/sections", Fixtures.laboratorySection(), null);
        var published = api.post("/api/schemas/" + id + "/publish", Map.of(), null);
        var reread = api.get("/api/schemas/" + id);

        assertThat(opened.status()).isEqualTo(200);
        assertThat(rejected.status()).isEqualTo(400);
        assertThat(rejected.text("code")).isEqualTo("INVALID_INPUT");
        assertThat(withSection.status()).as(withSection.body()).isEqualTo(200);
        assertThat(published.status()).as(published.body()).isEqualTo(201);
        assertThat(published.location()).isEqualTo("/api/schemas/" + id + "/versions/1");
        assertThat(reread.json().get("draft")).isNull();
        assertThat(reread.json().get("effectiveVersion")).isEqualTo(1);
        assertThat(api.get("/api/schemas/" + id + "/versions/1").status()).isEqualTo(200);
        assertThat(api.get("/api/schemas/" + id + "/versions/9").status()).isEqualTo(404);
    }

    @Test
    void aVersionWithAFutureDateIsListedButNotYetEffective() {
        String id = createSchema("Laboratory inspection", "LABORATORY");
        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.laboratorySection(), null);
        api.post("/api/schemas/" + id + "/publish", Map.of(), null);

        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.laboratorySection(), null);
        Instant nextMonth = Instant.now().plus(30, ChronoUnit.DAYS);
        var second = api.post("/api/schemas/" + id + "/publish", Map.of("effectiveFrom", nextMonth.toString()), null);
        var reread = api.get("/api/schemas/" + id);

        assertThat(second.status()).as(second.body()).isEqualTo(201);
        assertThat(((List<?>) reread.json().get("versions"))).hasSize(2);
        assertThat(reread.json().get("effectiveVersion")).isEqualTo(1);
    }

    @Test
    void aDraftThatLeavesASubsystemUnevaluatedIsNotPublishedAndStaysOpen() {
        String id = createSchema("Factory inspection", "FACTORY");
        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.laboratorySection(), null);

        var refused = api.post("/api/schemas/" + id + "/publish", Map.of(), null);
        var reread = api.get("/api/schemas/" + id);

        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.text("code")).isEqualTo("SCHEMA_NOT_PUBLISHABLE");
        assertThat((List<?>) refused.json().get("details")).isNotEmpty();
        assertThat(reread.json().get("draft")).isNotNull();
    }

    @Test
    void anAssetTypeBelongsToOneSchemaOnly() {
        createSchema("First for facilities", "FACILITY");

        var second = api.post("/api/schemas", Map.of("name", "Second", "assetTypes", List.of("FACILITY")), null);

        assertThat(second.status()).isBetween(409, 422);
    }
}
