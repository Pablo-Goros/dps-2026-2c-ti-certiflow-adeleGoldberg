package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base of the API tests: the whole application on a random port over an in-memory H2 database
 * (the same adapters and migrations as production, nothing mocked).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:certiflow-api;DB_CLOSE_DELAY=-1"
        })
abstract class ApiTest {

    @Value("${local.server.port}")
    int port;

    Api api;

    @BeforeEach
    void connect() {
        api = new Api(port);
    }

    /** Registers a person and returns the new id. Names are unique per call so tests can share the database. */
    String person(String name) {
        var reply = api.post("/api/parties", java.util.Map.of("name", name + " " + System.nanoTime(),
                "kind", "PERSON"), null);
        return reply.text("id");
    }

    String organization(String name) {
        var reply = api.post("/api/parties", java.util.Map.of("name", name + " " + System.nanoTime(),
                "kind", "ORGANIZATION"), null);
        return reply.text("id");
    }

    /** A facility that has been inspected and whose inspection is closed. */
    record Inspected(String owner, String inspector, String assetId, String inspectionId) {
    }

    /**
     * Publishes the schema of facilities (one housekeeping check per subsystem) unless this test
     * class's database already has one.
     */
    void publishFacilitySchemaOnce() {
        boolean exists = api.get("/api/schemas").jsonList().stream()
                .anyMatch(schema -> ((java.util.List<?>) schema.get("assetTypes")).contains("FACILITY"));
        if (exists) {
            return;
        }
        String id = api.post("/api/schemas", java.util.Map.of("name", "Facility",
                "assetTypes", java.util.List.of("FACILITY")), null).text("id");
        api.post("/api/schemas/" + id + "/draft", java.util.Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.facilitySection(), null);
        var published = api.post("/api/schemas/" + id + "/publish", java.util.Map.of(), null);
        org.assertj.core.api.Assertions.assertThat(published.status()).as(published.body()).isEqualTo(201);
    }

    /** Registers a facility of the jurisdiction, inspects it with the given answers and closes the inspection. */
    Inspected closedFacilityInspection(String jurisdiction, String electrical, String pressure, String safety) {
        String owner = organization("Owner");
        String inspector = person("Inspector");
        var asset = new java.util.HashMap<String, Object>();
        asset.put("name", "Plant " + System.nanoTime());
        asset.put("assetType", "FACILITY");
        asset.put("responsibleId", owner);
        asset.put("location", "Building 1");
        asset.put("characteristics", java.util.Map.of("room", "1"));
        asset.put("jurisdiction", jurisdiction);
        String assetId = api.post("/api/assets", asset, null).text("id");
        String id = api.post("/api/inspections", java.util.Map.of("assetId", assetId, "inspectorId", inspector,
                "expectedDate", "2027-01-15"), null).text("id");
        org.assertj.core.api.Assertions.assertThat(
                api.post("/api/inspections/" + id + "/start", java.util.Map.of(), inspector).status()).isEqualTo(200);
        api.put("/api/inspections/" + id + "/answers/ELEC", Fixtures.option(electrical), inspector);
        api.put("/api/inspections/" + id + "/answers/PRES", Fixtures.option(pressure), inspector);
        api.put("/api/inspections/" + id + "/answers/SAFE", Fixtures.option(safety), inspector);
        var closed = api.post("/api/inspections/" + id + "/close", java.util.Map.of(), inspector);
        org.assertj.core.api.Assertions.assertThat(closed.status()).as(closed.body()).isEqualTo(200);
        return new Inspected(owner, inspector, assetId, id);
    }
}
