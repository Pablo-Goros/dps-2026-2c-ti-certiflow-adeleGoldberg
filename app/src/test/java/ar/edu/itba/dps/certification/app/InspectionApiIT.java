package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The inspection life cycle driven only through HTTP, over the real database. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-inspection-it;DB_CLOSE_DELAY=-1")
class InspectionApiIT extends ApiTest {

    private static boolean schemaPublished;

    @BeforeEach
    void publishTheLaboratorySchemaOnce() {
        if (schemaPublished) {
            return;
        }
        String id = api.post("/api/schemas", Map.of("name", "Laboratory", "assetTypes", List.of("LABORATORY")),
                null).text("id");
        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.laboratorySection(), null);
        assertThat(api.post("/api/schemas/" + id + "/publish", Map.of(), null).status()).isEqualTo(201);
        schemaPublished = true;
    }

    private String laboratory(String owner) {
        var body = new HashMap<String, Object>();
        body.put("name", "Lab " + System.nanoTime());
        body.put("assetType", "LABORATORY");
        body.put("responsibleId", owner);
        body.put("location", "Floor 2");
        body.put("characteristics", Map.of("room", "3"));
        body.put("jurisdiction", "REFERENCE");
        var reply = api.post("/api/assets", body, null);
        assertThat(reply.status()).as(reply.body()).isEqualTo(201);
        return reply.text("id");
    }

    private String assigned(String assetId, String inspector) {
        var reply = api.post("/api/inspections",
                Map.of("assetId", assetId, "inspectorId", inspector, "expectedDate", "2027-01-15"), null);
        assertThat(reply.status()).as(reply.body()).isEqualTo(201);
        return reply.text("id");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> criterion(Api.Reply inspection, String id) {
        return ((List<Map<String, Object>>) inspection.json().get("criteria")).stream()
                .filter(line -> id.equals(line.get("criterionId"))).findFirst().orElseThrow();
    }

    @Test
    void anInspectionIsAssignedStartedAnsweredAndClosed() {
        String inspector = person("Inspector");
        String id = assigned(laboratory(organization("Owner")), inspector);
        assertThat(api.get("/api/inspections/" + id).text("status")).isEqualTo("ASSIGNED");

        var started = api.post("/api/inspections/" + id + "/start", Map.of(), inspector);
        var temperature = api.put("/api/inspections/" + id + "/answers/TEMP", Fixtures.measurement("5"), inspector);
        var documentation = api.put("/api/inspections/" + id + "/answers/DOC", Fixtures.yes(), inspector);
        var evidence = api.post("/api/inspections/" + id + "/evidence", Map.of("criterionId", "DOC",
                "requirementLabel", "safety manual", "reference", "manual.pdf"), inspector);
        var closed = api.post("/api/inspections/" + id + "/close", Map.of(), inspector);

        assertThat(started.status()).as(started.body()).isEqualTo(200);
        assertThat(started.text("status")).isEqualTo("IN_PROGRESS");
        assertThat((List<?>) started.json().get("criteria")).hasSize(2);
        assertThat(temperature.status()).as(temperature.body()).isEqualTo(200);
        assertThat(documentation.status()).isEqualTo(200);
        assertThat(evidence.status()).as(evidence.body()).isEqualTo(201);
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);
        assertThat(closed.text("status")).isEqualTo("CLOSED");
        @SuppressWarnings("unchecked")
        var evaluation = (Map<String, Object>) criterion(closed, "TEMP").get("evaluation");
        assertThat(evaluation.get("result")).isEqualTo("APPROVED");
        assertThat(api.get("/api/inspections/" + id + "/act").status()).isEqualTo(200);
    }

    @Test
    void assignmentAndDraftExecutionDataCanBeChangedBeforeClosure() {
        String originalInspector = person("Inspector A");
        String reassignedInspector = person("Inspector B");
        String id = assigned(laboratory(organization("Owner")), originalInspector);

        var reassigned = api.put("/api/inspections/" + id + "/assignment",
                Map.of("inspectorId", reassignedInspector, "expectedDate", "2027-02-20"), null);
        var started = api.post("/api/inspections/" + id + "/start", Map.of(), reassignedInspector);
        var firstAnswer = api.put("/api/inspections/" + id + "/answers/TEMP",
                Fixtures.measurement("30"), reassignedInspector);
        var removedAnswer = api.deleteAs("/api/inspections/" + id + "/answers/TEMP", reassignedInspector);
        var finalAnswer = api.put("/api/inspections/" + id + "/answers/TEMP",
                Fixtures.measurement("5"), reassignedInspector);
        api.put("/api/inspections/" + id + "/answers/DOC", Fixtures.yes(), reassignedInspector);
        var firstEvidence = api.post("/api/inspections/" + id + "/evidence", Map.of("criterionId", "DOC",
                "requirementLabel", "safety manual", "reference", "wrong-manual.pdf"), reassignedInspector);
        var removedEvidence = api.deleteAs("/api/inspections/" + id + "/criteria/DOC/evidence/"
                + firstEvidence.text("id"), reassignedInspector);
        var finalEvidence = api.post("/api/inspections/" + id + "/evidence", Map.of("criterionId", "DOC",
                "requirementLabel", "safety manual", "reference", "manual.pdf"), reassignedInspector);
        var note = api.post("/api/inspections/" + id + "/notes",
                Map.of("criterionId", "TEMP", "text", "check the sensor"), reassignedInspector);
        var correctedNote = api.put("/api/inspections/" + id + "/notes/" + note.text("id"),
                Map.of("text", "sensor checked"), reassignedInspector);
        var removedNote = api.deleteAs("/api/inspections/" + id + "/notes/" + note.text("id"),
                reassignedInspector);
        var closed = api.post("/api/inspections/" + id + "/close", Map.of(), reassignedInspector);

        assertThat(reassigned.status()).as(reassigned.body()).isEqualTo(200);
        assertThat(started.status()).isEqualTo(200);
        assertThat(firstAnswer.status()).isEqualTo(200);
        assertThat(removedAnswer.status()).isEqualTo(200);
        assertThat(finalAnswer.status()).isEqualTo(200);
        assertThat(firstEvidence.status()).isEqualTo(201);
        assertThat(removedEvidence.status()).isEqualTo(200);
        assertThat(finalEvidence.status()).isEqualTo(201);
        assertThat(note.status()).isEqualTo(201);
        assertThat(correctedNote.text("text")).isEqualTo("sensor checked");
        assertThat(removedNote.status()).isEqualTo(200);
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);
        assertThat(closed.text("status")).isEqualTo("CLOSED");
        @SuppressWarnings("unchecked")
        var evaluation = (Map<String, Object>) criterion(closed, "TEMP").get("evaluation");
        assertThat(evaluation.get("result")).isEqualTo("APPROVED");
    }

    @Test
    void aRejectedMeasurementIsRectifiedAndTheEvaluationChanges() {
        String inspector = person("Inspector");
        String id = assigned(laboratory(organization("Owner")), inspector);
        api.post("/api/inspections/" + id + "/start", Map.of(), inspector);
        api.put("/api/inspections/" + id + "/answers/TEMP", Fixtures.measurement("30"), inspector);
        api.put("/api/inspections/" + id + "/answers/DOC", Fixtures.yes(), inspector);
        api.post("/api/inspections/" + id + "/evidence", Map.of("criterionId", "DOC",
                "requirementLabel", "safety manual", "reference", "manual.pdf"), inspector);
        var closed = api.post("/api/inspections/" + id + "/close", Map.of(), inspector);
        @SuppressWarnings("unchecked")
        var before = (Map<String, Object>) criterion(closed, "TEMP").get("evaluation");
        assertThat(before.get("result")).isEqualTo("REJECTED");

        var rectified = api.post("/api/inspections/" + id + "/rectifications", Map.of(
                "reason", "the thermometer was misread",
                "corrections", List.of(Map.of("type", "ANSWER", "criterionId", "TEMP",
                        "answer", Fixtures.measurement("5")))), inspector);

        assertThat(rectified.status()).as(rectified.body()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        var after = (Map<String, Object>) criterion(rectified, "TEMP").get("evaluation");
        assertThat(after.get("result")).isEqualTo("APPROVED");
        assertThat((List<?>) rectified.json().get("rectifications")).hasSize(1);
    }

    @Test
    void aKnownButInapplicableCriterionCannotBeRectifiedAndLeavesInspectionAndAuditUntouched() {
        publishFacilitySchemaOnce();
        String owner = organization("Owner");
        String inspector = person("Inspector");
        var assetBody = new HashMap<String, Object>();
        assetBody.put("name", "Partial facility " + System.nanoTime());
        assetBody.put("assetType", "FACILITY");
        assetBody.put("responsibleId", owner);
        assetBody.put("location", "Building 1");
        assetBody.put("characteristics", Map.of("room", "3"));
        assetBody.put("jurisdiction", "REFERENCE");
        assetBody.put("subsystems", List.of("electrical installation", "pressure system"));
        String assetId = api.post("/api/assets", assetBody, null).text("id");
        String id = assigned(assetId, inspector);
        api.post("/api/inspections/" + id + "/start", Map.of(), inspector);
        api.put("/api/inspections/" + id + "/answers/ELEC", Fixtures.option("clean"), inspector);
        api.put("/api/inspections/" + id + "/answers/PRES", Fixtures.option("clean"), inspector);
        assertThat(api.post("/api/inspections/" + id + "/close", Map.of(), inspector).status()).isEqualTo(200);
        var before = api.get("/api/inspections/" + id);
        int auditCount = api.get("/api/audit?type=INSPECTION&id=" + id).jsonList().size();

        var refused = api.post("/api/inspections/" + id + "/rectifications", Map.of(
                "reason", "the asset has no building safety subsystem",
                "corrections", List.of(Map.of("type", "ANSWER", "criterionId", "SAFE",
                        "answer", Fixtures.option("clean")))), inspector);

        assertThat(refused.status()).as(refused.body()).isEqualTo(422);
        assertThat(refused.text("code")).isEqualTo("BUSINESS_RULE");
        var after = api.get("/api/inspections/" + id);
        assertThat(after.json().get("rectifications")).isEqualTo(before.json().get("rectifications"));
        assertThat(criterion(after, "SAFE").get("answer")).isNull();
        assertThat(api.get("/api/audit?type=INSPECTION&id=" + id).jsonList()).hasSize(auditCount);
    void aMeasurementOffTheDeclaredRangeIsRecordedAndComesOutRejected() {
        String inspector = person("Inspector");
        String id = assigned(laboratory(organization("Owner")), inspector);
        api.post("/api/inspections/" + id + "/start", Map.of(), inspector);

        // The sensor range of the fixture is -50 to 150 c; 500 is off the scale.
        var offScale = api.put("/api/inspections/" + id + "/answers/TEMP", Fixtures.measurement("500"), inspector);
        api.put("/api/inspections/" + id + "/answers/DOC", Fixtures.yes(), inspector);
        api.post("/api/inspections/" + id + "/evidence", Map.of("criterionId", "DOC",
                "requirementLabel", "safety manual", "reference", "manual.pdf"), inspector);
        var closed = api.post("/api/inspections/" + id + "/close", Map.of(), inspector);

        assertThat(offScale.status()).as(offScale.body()).isEqualTo(200);
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        var evaluation = (Map<String, Object>) criterion(closed, "TEMP").get("evaluation");
        assertThat(evaluation.get("result")).isEqualTo("REJECTED");
        assertThat(evaluation.get("severity")).isEqualTo("CRITICAL");
        assertThat(evaluation.get("reasons").toString()).contains("off the sensor scale");
    }

    @Test
    void onlyTheAssignedInspectorCanStartIt() {
        String inspector = person("Inspector");
        String stranger = person("Stranger");
        String id = assigned(laboratory(organization("Owner")), inspector);

        var byStranger = api.post("/api/inspections/" + id + "/start", Map.of(), stranger);
        var anonymous = api.post("/api/inspections/" + id + "/start", Map.of(), null);

        assertThat(byStranger.status()).isEqualTo(422);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(api.get("/api/inspections/" + id).text("status")).isEqualTo("ASSIGNED");
    }

    @Test
    void anOrganizationCannotBeTheInspectorAndAnAssetHasOneOpenInspection() {
        String owner = organization("Owner");
        String asset = laboratory(owner);
        String inspector = person("Inspector");

        var byOrganization = api.post("/api/inspections",
                Map.of("assetId", asset, "inspectorId", owner, "expectedDate", "2027-01-15"), null);
        assigned(asset, inspector);
        var second = api.post("/api/inspections",
                Map.of("assetId", asset, "inspectorId", inspector, "expectedDate", "2027-02-15"), null);

        assertThat(byOrganization.status()).isIn(400, 422);
        assertThat(second.status()).isEqualTo(422);
    }

    @Test
    void inspectionsCanBeListedAndFiltered() {
        String inspector = person("Inspector");
        String asset = laboratory(organization("Owner"));
        String id = assigned(asset, inspector);

        var byAsset = api.get("/api/inspections?assetId=" + asset);
        var byBadStatus = api.get("/api/inspections?status=WHATEVER");

        assertThat(byAsset.jsonList()).extracting(row -> row.get("id")).containsExactly(id);
        assertThat(byBadStatus.status()).isEqualTo(400);
        assertThat(api.get("/api/inspections/missing").status()).isEqualTo(404);
    }
}
