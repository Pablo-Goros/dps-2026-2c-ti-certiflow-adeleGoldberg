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
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-applicability-timeline-it;DB_CLOSE_DELAY=-1")
class SchemaApplicabilityTimelineApiIT extends ApiTest {
    @Test
    void scheduledCoverageCannotAuthorizeAnIncompleteCurrentSchema() {
        var created = api.post("/api/schemas", Map.of("name", "Generic", "assetTypes", List.of("EQUIPMENT")), null);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String path = "/api/schemas/" + created.text("id");
        assertThat(api.post(path + "/draft", Map.of(), null).status()).isEqualTo(200);
        assertThat(api.post(path + "/draft/sections", Fixtures.housekeepingSection(), null).status()).isEqualTo(200);
        assertThat(api.post(path + "/publish", Map.of(), null).status()).isEqualTo(201);
        assertThat(api.post(path + "/draft", Map.of(), null).status()).isEqualTo(200);
        var futureSection = new java.util.HashMap<>(Fixtures.facilitySection());
        futureSection.put("order", 2);
        assertThat(api.post(path + "/draft/sections", futureSection, null).status()).isEqualTo(200);
        assertThat(api.post(path + "/publish", Map.of("effectiveFrom",
                Instant.now().plus(30, ChronoUnit.DAYS).toString()), null).status()).isEqualTo(201);

        var refused = api.post(path + "/applicability", Map.of("assetType", "FACTORY"), null);

        assertThat(refused.status()).as(refused.body()).isEqualTo(422);
        assertThat(refused.body()).contains("evaluates no criterion for");
        assertThat(api.get(path).body()).doesNotContain("FACTORY");
    }
}
