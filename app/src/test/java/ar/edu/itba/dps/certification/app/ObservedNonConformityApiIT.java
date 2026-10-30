package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-observed-nonconformity-it;DB_CLOSE_DELAY=-1")
class ObservedNonConformityApiIT extends ApiTest {

    private String inspector;
    private String inspectionId;

    @BeforeEach
    void startAFacilityInspection() {
        publishFacilitySchemaOnce();
        String owner = organization("Owner");
        inspector = person("Inspector");
        var asset = new HashMap<String, Object>();
        asset.put("name", "Plant " + System.nanoTime());
        asset.put("assetType", "FACILITY");
        asset.put("responsibleId", owner);
        asset.put("location", "Building 1");
        asset.put("characteristics", Map.of("room", "1"));
        asset.put("jurisdiction", "REFERENCE");
        asset.put("subsystems", List.of("electrical installation", "pressure system"));
        String assetId = api.post("/api/assets", asset, null).text("id");
        inspectionId = api.post("/api/inspections", Map.of("assetId", assetId, "inspectorId", inspector,
                "expectedDate", "2027-01-15"), null).text("id");
        assertThat(api.post(inspection("/start"), Map.of(), inspector).status()).isEqualTo(200);
        api.put(inspection("/answers/ELEC"), Fixtures.option("clean"), inspector);
        api.put(inspection("/answers/PRES"), Fixtures.option("clean"), inspector);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anObservationRejectsACriterionWhoseAnswerTheRuleApproves() {
        String noteId = api.post(inspection("/notes"),
                Map.of("text", "cable con la aislación pelada junto al tablero"), inspector).text("id");

        var recorded = api.post(observations("ELEC"), Map.of("result", "REJECTED", "severity", "CRITICAL",
                "description", "conductor expuesto junto al tablero", "noteId", noteId), inspector);
        var viewed = api.get(inspection(""));
        var closed = api.post(inspection("/close"), Map.of(), inspector);

        assertThat(recorded.status()).as(recorded.body()).isEqualTo(201);
        assertThat(recorded.text("noteId")).isEqualTo(noteId);
        assertThat(recorded.text("recordedBy")).isEqualTo(inspector);
        assertThat((List<?>) criterion(viewed, "ELEC").get("observedNonConformities")).hasSize(1);
        var evaluation = (Map<String, Object>) criterion(closed, "ELEC").get("evaluation");
        assertThat(evaluation.get("result")).isEqualTo("REJECTED");
        assertThat(evaluation.get("severity")).isEqualTo("CRITICAL");
        assertThat(evaluation.get("reasons").toString()).contains("conductor expuesto junto al tablero");
        assertThat(((Map<String, Object>) criterion(closed, "ELEC").get("answer")).get("option")).isEqualTo("clean");
    }

    @Test
    void theApiRefusesWhatTheAggregateRefuses() {
        var approving = api.post(observations("ELEC"), body("APPROVED", "HIGH"), inspector);
        var inapplicable = api.post(observations("SAFE"), body("REJECTED", "HIGH"), inspector);
        var anonymous = api.post(observations("ELEC"), body("REJECTED", "HIGH"), null);
        var stranger = api.post(observations("ELEC"), body("REJECTED", "HIGH"), person("Stranger"));
        var withoutSeverity = api.post(observations("ELEC"),
                Map.of("result", "REJECTED", "description", "seen on site"), inspector);
        var unknownInspection = api.post("/api/inspections/missing/criteria/ELEC/observed-nonconformities",
                body("REJECTED", "HIGH"), inspector);

        assertThat(approving.status()).as(approving.body()).isEqualTo(422);
        assertThat(inapplicable.status()).as(inapplicable.body()).isEqualTo(422);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(stranger.status()).isEqualTo(422);
        assertThat(withoutSeverity.status()).as(withoutSeverity.body()).isEqualTo(400);
        assertThat(unknownInspection.status()).isEqualTo(404);
        assertThat((List<?>) criterion(api.get(inspection("")), "ELEC").get("observedNonConformities")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void itIsWithdrawnWhileInProgressAndOnlyByRectificationAfterClosure() {
        String first = api.post(observations("ELEC"), body("OBSERVED", "LOW"), inspector).text("id");
        var withdrawn = api.deleteAs(observations("ELEC") + "/" + first, inspector);
        String second = api.post(observations("ELEC"), body("REJECTED", "CRITICAL"), inspector).text("id");
        api.post(inspection("/close"), Map.of(), inspector);

        var lateRecord = api.post(observations("ELEC"), body("OBSERVED", "LOW"), inspector);
        var rectified = api.post(inspection("/rectifications"), Map.of(
                "reason", "the cable belongs to the neighbouring building",
                "corrections", List.of(Map.of("type", "OBSERVATION_WITHDRAWAL", "criterionId", "ELEC",
                        "observationId", second))), inspector);

        assertThat(withdrawn.status()).as(withdrawn.body()).isEqualTo(200);
        assertThat(lateRecord.status()).isEqualTo(422);
        assertThat(rectified.status()).as(rectified.body()).isEqualTo(201);
        var evaluation = (Map<String, Object>) criterion(rectified, "ELEC").get("evaluation");
        assertThat(evaluation.get("result")).isEqualTo("APPROVED");
        assertThat((List<?>) criterion(rectified, "ELEC").get("observedNonConformities")).isEmpty();
        assertThat(api.get("/api/audit?type=INSPECTION&id=" + inspectionId).jsonList())
                .filteredOn(entry -> "INSPECTION_RECTIFIED".equals(entry.get("action")))
                .anySatisfy(entry -> assertThat(entry.toString()).contains("observation.ELEC." + second));
    }

    @Test
    void recordingAndWithdrawingAreAudited() {
        String id = api.post(observations("ELEC"), body("OBSERVED", "LOW"), inspector).text("id");
        api.deleteAs(observations("ELEC") + "/" + id, inspector);

        var audit = api.get("/api/audit?type=INSPECTION&id=" + inspectionId).jsonList();

        assertThat(audit).filteredOn(entry -> "INSPECTION_DATA_RECORDED".equals(entry.get("action")))
                .anySatisfy(entry -> assertThat(entry.toString()).contains("observation.ELEC." + id));
        assertThat(audit).filteredOn(entry -> "INSPECTION_DATA_CORRECTED".equals(entry.get("action")))
                .anySatisfy(entry -> assertThat(entry.toString())
                        .contains("observation.ELEC." + id, "OBSERVED LOW: seen on site"));
    }

    @Test
    void anEmptyNoteIdCountsAsNone() {
        var recorded = api.post(observations("ELEC"), Map.of("result", "REJECTED", "severity", "HIGH",
                "description", "seen on site", "noteId", ""), inspector);

        assertThat(recorded.status()).as(recorded.body()).isEqualTo(201);
        assertThat(recorded.text("noteId")).isNull();
    }

    private String inspection(String suffix) {
        return "/api/inspections/" + inspectionId + suffix;
    }

    private String observations(String criterionId) {
        return inspection("/criteria/" + criterionId + "/observed-nonconformities");
    }

    private static Map<String, Object> body(String result, String severity) {
        return Map.of("result", result, "severity", severity, "description", "seen on site");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> criterion(Api.Reply inspection, String id) {
        return ((List<Map<String, Object>>) inspection.json().get("criteria")).stream()
                .filter(line -> id.equals(line.get("criterionId"))).findFirst().orElseThrow();
    }
}
