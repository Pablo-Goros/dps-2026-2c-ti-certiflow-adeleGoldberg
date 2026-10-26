package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application with the demo profile, which is the only thing that runs the seeder, and
 * checks that the scenario the delivery asks to demonstrate is actually there.
 *
 * <p>Without this the seeder has no coverage at all: it drives use cases that the rest of the suite
 * exercises through HTTP, so a changed signature or a new publication rule would keep the build
 * green and only break when someone starts the application to run the demo.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-demo-it;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
class DemoSeedIT {

    @Value("${local.server.port}")
    int port;

    private Api api;

    @BeforeEach
    void connect() {
        api = new Api(port);
    }

    @Test
    @DisplayName("the demo profile seeds one asset per jurisdiction over a single shared schema")
    void theDemoSeedsOneAssetPerJurisdiction() {
        List<Map<String, Object>> assets = api.get("/api/assets").jsonList();

        assertThat(assets).hasSize(3);
        assertThat(assets).extracting(asset -> asset.get("jurisdiction"))
                .containsExactlyInAnyOrder("AR-BA", "AR-CBA", "REFERENCE");
        assertThat(assets).allSatisfy(asset -> {
            assertThat(asset.get("assetType")).isEqualTo("FACILITY");
            assertThat((List<?>) asset.get("subsystems")).hasSize(3);
        });

        List<Map<String, Object>> schemas = api.get("/api/schemas").jsonList();
        assertThat(schemas).hasSize(1);
    }

    @Test
    @DisplayName("the schema is published twice, the second version effective in the future")
    void theSchemaHasAFutureEffectiveVersion() {
        String schema = api.get("/api/schemas").jsonList().getFirst().get("id").toString();

        var reread = api.get("/api/schemas/" + schema);
        var second = api.get("/api/schemas/" + schema + "/versions/2");

        assertThat(reread.json().get("effectiveVersion")).isEqualTo(1);
        assertThat(second.status()).as(second.body()).isEqualTo(200);
        assertThat(second.json().get("effectiveFrom")).isNotNull();
    }

    @Test
    @DisplayName("every inspection froze the first version and carries findings of several severities")
    void theInspectionsAreClosedWithSeveralSeverities() {
        List<Map<String, Object>> inspections = api.get("/api/inspections").jsonList();
        assertThat(inspections).hasSize(3);
        assertThat(inspections).allSatisfy(inspection ->
                assertThat(inspection.get("status")).isEqualTo("CLOSED"));

        List<Map<String, Object>> findings = api.get("/api/findings").jsonList();
        assertThat(findings).extracting(finding -> finding.get("severity"))
                .contains("LOW", "MEDIUM", "HIGH");
    }

    @Test
    @DisplayName("the same result is certified in one jurisdiction and refused in another")
    void theSameResultIsDecidedDifferentlyPerJurisdiction() {
        List<Map<String, Object>> certificates = api.get("/api/certificates").jsonList();

        assertThat(certificates).isNotEmpty();
        assertThat(certificates).allSatisfy(certificate ->
                assertThat(certificate.get("scope")).isEqualTo("PARTIAL"));
        assertThat(certificates).extracting(DemoSeedIT::jurisdictionOf)
                .contains("AR-BA", "REFERENCE")
                .doesNotContain("AR-CBA");
    }

    @Test
    @DisplayName("a certified asset derives its global certificate and a blocked one explains why not")
    void theGlobalCertificateIsDerivedWhereEveryPartIsCertified() {
        List<String> derivations = api.get("/api/inspections").jsonList().stream()
                .map(inspection -> api.get("/api/inspections/" + inspection.get("id")
                        + "/global-derivation").json().get("type").toString())
                .toList();

        assertThat(derivations).contains("Derived", "NotDerivable");
    }

    @SuppressWarnings("unchecked")
    private static String jurisdictionOf(Map<String, Object> certificate) {
        var policy = (Map<String, Object>) certificate.get("policy");
        var reference = (Map<String, Object>) policy.get("reference");
        return reference.get("jurisdiction").toString();
    }
}
