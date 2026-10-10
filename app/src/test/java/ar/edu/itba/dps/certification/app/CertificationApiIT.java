package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Findings, corrective actions and partial certificates (F1) under a jurisdiction policy (F3), over HTTP. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-certification-it;DB_CLOSE_DELAY=-1")
class CertificationApiIT extends ApiTest {

    @BeforeEach
    void publishTheFacilitySchema() {
        publishFacilitySchemaOnce();
    }

    private Inspected closedInspection(String electrical, String pressure, String safety) {
        return closedFacilityInspection("REFERENCE", electrical, pressure, safety);
    }

    @Test
    void eachSubsystemIsCertifiedOnItsOwnAndTheGlobalOneIsDerivedFromThem() {
        var inspected = closedInspection("clean", "clean", "clean");
        String path = "/api/inspections/" + inspected.inspectionId();

        var electrical = api.post(path + "/certificates", Map.of("subsystem", "electrical installation"), null);
        var again = api.post(path + "/certificates", Map.of("subsystem", "electrical installation"), null);
        var partialOnly = api.get(path + "/global-derivation");
        api.post(path + "/certificates", Map.of("subsystem", "pressure system"), null);
        api.post(path + "/certificates", Map.of("subsystem", "building safety"), null);
        var complete = api.get(path + "/global-derivation");
        var listed = api.get("/api/certificates?assetId=" + inspected.assetId());

        assertThat(electrical.status()).as(electrical.body()).isEqualTo(201);
        assertThat(electrical.text("outcome")).isEqualTo("ISSUED");
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.text("outcome")).isEqualTo("ALREADY_ISSUED");
        assertThat(partialOnly.text("type")).isEqualTo("NotDerivable");
        assertThat(complete.text("type")).isEqualTo("Derived");
        assertThat(listed.jsonList()).hasSize(3);
        assertThat(listed.jsonList()).allSatisfy(c -> {
            assertThat(c.get("scope")).isEqualTo("PARTIAL");
            assertThat(c.get("status")).isEqualTo("VALID");
        });
    }

    @Test
    void aRejectedSubsystemBlocksOnlyItsOwnCertificateUntilTheCorrectionIsVerified() {
        var inspected = closedInspection("hazardous", "clean", "clean");
        String path = "/api/inspections/" + inspected.inspectionId();
        String executor = person("Executor");

        var findings = api.get("/api/findings?inspectionId=" + inspected.inspectionId());
        String findingId = (String) findings.jsonList().get(0).get("id");
        var blocked = api.post(path + "/certificates", Map.of("subsystem", "electrical installation"), null);
        var independent = api.post(path + "/certificates", Map.of("subsystem", "pressure system"), null);
        var eligibility = api.get(path + "/eligibility?subsystem=electrical%20installation");

        LocalDate due = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        var planned = api.post("/api/findings/" + findingId + "/plan",
                Map.of("work", "replace the wiring", "executorId", executor, "dueDate", due.toString()),
                inspected.owner());
        var executed = api.post("/api/findings/" + findingId + "/execution",
                Map.of("statement", "wiring replaced", "evidenceReferences", List.of("photo.jpg")), executor);
        var verified = api.post("/api/findings/" + findingId + "/verification",
                Map.of("satisfactory", true, "reason", "checked on site"), inspected.inspector());
        var afterCorrection = api.post(path + "/certificates", Map.of("subsystem", "electrical installation"), null);

        assertThat(findings.jsonList()).hasSize(1);
        assertThat(findings.jsonList().get(0).get("result")).isEqualTo("REJECTED");
        assertThat(blocked.status()).isEqualTo(422);
        assertThat(blocked.text("outcome")).isEqualTo("BLOCKED");
        assertThat(independent.status()).as(independent.body()).isEqualTo(201);
        assertThat(eligibility.status()).isEqualTo(200);
        assertThat(planned.status()).as(planned.body()).isEqualTo(200);
        assertThat(executed.status()).as(executed.body()).isEqualTo(200);
        assertThat(verified.status()).as(verified.body()).isEqualTo(200);
        assertThat(afterCorrection.status()).as(afterCorrection.body()).isEqualTo(201);
    }

    @Test
    void onlyTheResponsiblePlansAndOnlyTheInspectorVerifies() {
        var inspected = closedInspection("hazardous", "clean", "clean");
        String stranger = person("Stranger");
        String findingId = (String) api.get("/api/findings?inspectionId=" + inspected.inspectionId())
                .jsonList().get(0).get("id");
        LocalDate due = LocalDate.now(ZoneOffset.UTC).plusDays(10);

        var byStranger = api.post("/api/findings/" + findingId + "/plan",
                Map.of("work", "fix", "executorId", stranger, "dueDate", due.toString()), stranger);
        var anonymous = api.post("/api/findings/" + findingId + "/plan",
                Map.of("work", "fix", "executorId", stranger, "dueDate", due.toString()), null);
        var unknown = api.get("/api/findings/nope");

        assertThat(byStranger.status()).isEqualTo(422);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(404);
    }

    @Test
    void theCertificateReportAndTheAuditTrailAreAvailable() {
        var inspected = closedInspection("clean", "clean", "clean");
        String path = "/api/inspections/" + inspected.inspectionId();
        var issued = api.post(path + "/certificates", Map.of("subsystem", "building safety"), null);
        String certificateId = (String) ((Map<?, ?>) issued.json().get("certificate")).get("id");

        var report = api.get("/api/certificates/" + certificateId + "/report");
        var audit = api.get("/api/audit?type=INSPECTION&id=" + inspected.inspectionId());
        var summary = api.get(path + "/findings-summary");

        assertThat(report.status()).as(report.body()).isEqualTo(200);
        assertThat(audit.status()).isEqualTo(200);
        assertThat(audit.jsonListOrEmpty()).isNotEmpty();
        assertThat(summary.status()).isEqualTo(200);
        assertThat(api.get("/api/certificates/missing").status()).isEqualTo(404);
    }

    @Test
    void everyEventOfTheRequestsIsDeliveredThroughTheOutbox() {
        closedInspection("hazardous", "clean", "clean");
        api.post("/api/admin/jobs/run", Map.of(), null);

        var done = api.get("/api/admin/outbox?status=DONE");
        var pending = api.get("/api/admin/outbox?status=PENDING");
        var dead = api.get("/api/admin/outbox?status=DEAD");

        assertThat(done.jsonListOrEmpty()).isNotEmpty();
        assertThat(pending.jsonListOrEmpty()).isEmpty();
        assertThat(dead.jsonListOrEmpty()).isEmpty();
    }
}
