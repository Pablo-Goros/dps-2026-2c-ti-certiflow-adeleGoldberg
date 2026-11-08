package ar.edu.itba.dps.certification.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assistant enabled exactly as a deployment would enable it, with nothing replaced: the
 * application starts on its own persistence, and without a provider every consultation is a 503
 * that records nothing.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:certiflow-assistant-none;DB_CLOSE_DELAY=-1",
            "certiflow.assistant.enabled=true",
            "certiflow.assistant.provider=none"
        })
class AssistantWithoutProviderApiIT extends ApiTest {

    @Test
    void theApplicationStartsAndAConsultationWithoutProviderIsMisconfigured() {
        publishFacilitySchemaOnce();
        String inspector = person("Inspector");
        var asset = new HashMap<String, Object>();
        asset.put("name", "Plant " + System.nanoTime());
        asset.put("assetType", "FACILITY");
        asset.put("responsibleId", organization("Owner"));
        asset.put("location", "Building 1");
        asset.put("characteristics", Map.of("room", "1"));
        asset.put("jurisdiction", "REFERENCE");
        asset.put("subsystems", List.of("electrical installation", "pressure system"));
        String assetId = api.post("/api/assets", asset, null).text("id");
        String inspectionId = api.post("/api/inspections", Map.of("assetId", assetId, "inspectorId", inspector,
                "expectedDate", "2027-01-15"), null).text("id");
        String base = "/api/inspections/" + inspectionId;
        api.post(base + "/start", Map.of(), inspector);
        api.post(base + "/notes", Map.of("text", "cable pelado"), inspector);

        var consulted = api.post(base + "/consultations", Map.of(), inspector);

        assertThat(consulted.status()).as(consulted.body()).isEqualTo(503);
        assertThat(consulted.text("code")).isEqualTo("ASSISTANT_UNAVAILABLE");
        assertThat(consulted.json().get("details")).isEqualTo(List.of("MISCONFIGURED"));
        assertThat(api.get(base + "/consultations").jsonList()).isEmpty();
    }
}
