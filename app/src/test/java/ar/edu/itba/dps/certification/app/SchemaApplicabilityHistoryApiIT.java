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
class SchemaApplicabilityHistoryApiIT extends ApiTest {

    @Test
    void addingAssetTypeApplicabilityRejectsAnUncoveredEarlierPublishedVersion() {
        String id = api.post("/api/schemas",
                Map.of("name", "Versioned equipment inspection", "assetTypes", List.of("EQUIPMENT")), null)
                .text("id");
        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.housekeepingSection(), null);
        assertThat(api.post("/api/schemas/" + id + "/publish", Map.of(), null).status()).isEqualTo(201);

        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.facilitySection(), null);
        Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
        assertThat(api.post("/api/schemas/" + id + "/publish", Map.of("effectiveFrom", future.toString()), null)
                .status()).isEqualTo(201);

        var refused = api.post("/api/schemas/" + id + "/applicability", Map.of("assetType", "FACTORY"), null);

        assertThat(refused.status()).as(refused.body()).isEqualTo(422);
        assertThat(refused.text("code")).isEqualTo("BUSINESS_RULE");
        assertThat(refused.text("message")).contains("version 1").contains("electrical installation");
        assertThat(api.get("/api/schemas/" + id).json().get("assetTypes")).isEqualTo(List.of("EQUIPMENT"));
    }
}
