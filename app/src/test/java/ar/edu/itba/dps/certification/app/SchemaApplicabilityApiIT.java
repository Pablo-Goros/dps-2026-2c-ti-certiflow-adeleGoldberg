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
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-applicability-history-it;DB_CLOSE_DELAY=-1")
class SchemaApplicabilityApiIT extends ApiTest {

    @Test
    void applicabilityCanBeAddedRemovedAndTransferredThroughTheApi() {
        String source = createAndPublish("Source", List.of("EQUIPMENT", "LABORATORY"),
                Fixtures.housekeepingSection());
        String target = createAndPublish("Target", List.of("FACILITY"), Fixtures.facilitySection());

        var applied = api.post("/api/schemas/" + target + "/applicability",
                Map.of("assetType", "FACTORY"), null);
        var refusedRemoval = api.delete("/api/schemas/" + target + "/applicability/FACTORY");
        var transferred = api.post("/api/schemas/" + source + "/applicability/transfer",
                Map.of("targetSchemaId", target, "assetType", "EQUIPMENT"), null);
        var sourceAfter = api.get("/api/schemas/" + source);
        var targetAfter = api.get("/api/schemas/" + target);

        assertThat(applied.status()).as(applied.body()).isEqualTo(200);
        assertThat(refusedRemoval.status()).as(refusedRemoval.body()).isEqualTo(422);
        assertThat(refusedRemoval.body()).contains("transfer it instead");
        assertThat(transferred.status()).as(transferred.body()).isEqualTo(200);
        assertThat(sourceAfter.body()).doesNotContain("EQUIPMENT");
        assertThat(targetAfter.body()).contains("EQUIPMENT");
    }

    private String createAndPublish(String name, List<String> assetTypes, Map<String, Object> section) {
        var created = api.post("/api/schemas", Map.of("name", name, "assetTypes", assetTypes), null);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String id = created.text("id");
        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        var added = api.post("/api/schemas/" + id + "/draft/sections", section, null);
        assertThat(added.status()).as(added.body()).isEqualTo(200);
        var published = api.post("/api/schemas/" + id + "/publish", Map.of(), null);
        assertThat(published.status()).as(published.body()).isEqualTo(201);
        return id;
    }
}
