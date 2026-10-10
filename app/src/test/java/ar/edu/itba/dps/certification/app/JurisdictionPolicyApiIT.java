package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F3 over HTTP: the same inspection result gets a different decision and a different validity
 * depending on the jurisdiction of the asset, with no change to the code that issues certificates.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-policy-it;DB_CLOSE_DELAY=-1")
class JurisdictionPolicyApiIT extends ApiTest {

    private static boolean schemaPublished;

    @BeforeEach
    void publishTheEquipmentSchemaOnce() {
        if (schemaPublished) {
            return;
        }
        String id = api.post("/api/schemas", Map.of("name", "Equipment", "assetTypes", List.of("EQUIPMENT")),
                null).text("id");
        api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        api.post("/api/schemas/" + id + "/draft/sections", Fixtures.housekeepingSection(), null);
        var published = api.post("/api/schemas/" + id + "/publish", Map.of(), null);
        assertThat(published.status()).as(published.body()).isEqualTo(201);
        schemaPublished = true;
    }

    /** Inspects a new piece of equipment of the jurisdiction, closes it and has its responsible plan every correction. */
    private String inspectedAndPlanned(String jurisdiction, String answer) {
        String owner = organization("Owner");
        String inspector = person("Inspector");
        String executor = person("Executor");
        var asset = new HashMap<String, Object>();
        asset.put("name", "Press " + System.nanoTime());
        asset.put("assetType", "EQUIPMENT");
        asset.put("responsibleId", owner);
        asset.put("location", "Workshop");
        asset.put("characteristics", Map.of("room", "1"));
        asset.put("jurisdiction", jurisdiction);
        var registered = api.post("/api/assets", asset, null);
        assertThat(registered.status()).as(registered.body()).isEqualTo(201);

        String inspection = api.post("/api/inspections", Map.of("assetId", registered.text("id"),
                "inspectorId", inspector, "expectedDate", "2027-01-15"), null).text("id");
        api.post("/api/inspections/" + inspection + "/start", Map.of(), inspector);
        api.put("/api/inspections/" + inspection + "/answers/HK", Fixtures.option(answer), inspector);
        var closed = api.post("/api/inspections/" + inspection + "/close", Map.of(), inspector);
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);

        LocalDate due = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        for (var finding : api.get("/api/findings?inspectionId=" + inspection).jsonList()) {
            var planned = api.post("/api/findings/" + finding.get("id") + "/plan",
                    Map.of("work", "tidy up", "executorId", executor, "dueDate", due.toString()), owner);
            assertThat(planned.status()).as(planned.body()).isEqualTo(200);
        }
        return inspection;
    }

    @ParameterizedTest(name = "{0} with an {1} area -> {2} {3} for {4} months")
    @CsvSource({
        "REFERENCE, clean,    201, REGULAR,     12",
        "AR-BA,     clean,    201, REGULAR,     12",
        "AR-CBA,    clean,    201, REGULAR,      6",
        "REFERENCE, untidy,   201, CONDITIONAL, 12",
        "AR-BA,     untidy,   201, CONDITIONAL,  6",
    })
    void eachJurisdictionIssuesWithItsOwnModeAndDuration(String jurisdiction, String answer, int status,
            String mode, int months) {
        String inspection = inspectedAndPlanned(jurisdiction, answer);

        var reply = api.post("/api/inspections/" + inspection + "/certificates", Map.of(), null);

        assertThat(reply.status()).as(reply.body()).isEqualTo(status);
        assertThat(reply.text("outcome")).isEqualTo("ISSUED");
        @SuppressWarnings("unchecked")
        var certificate = (Map<String, Object>) reply.json().get("certificate");
        assertThat(certificate.get("mode")).isEqualTo(mode);
        assertThat(certificate.get("scope")).isEqualTo("GLOBAL");
        Instant issued = Instant.parse((String) certificate.get("issuedAt"));
        Instant expires = Instant.parse((String) certificate.get("expiresAt"));
        assertThat(expires).isEqualTo(issued.atZone(ZoneOffset.UTC).plusMonths(months).toInstant());
        assertThat(certificate.get("policy").toString()).contains(jurisdiction);
    }

    @ParameterizedTest(name = "{0} with an {1} area is blocked by {2}")
    @CsvSource({
        "AR-CBA,    untidy,    BlockingSeverity",
        "AR-CBA,    untidy,    ConditionalNotAllowed",
        "AR-BA,     hazardous, BlockingSeverity",
        "REFERENCE, hazardous, UnverifiedRejection",
    })
    void eachJurisdictionBlocksForItsOwnReason(String jurisdiction, String answer, String blocker) {
        String inspection = inspectedAndPlanned(jurisdiction, answer);

        var reply = api.post("/api/inspections/" + inspection + "/certificates", Map.of(), null);

        assertThat(reply.status()).as(reply.body()).isEqualTo(422);
        assertThat(reply.text("outcome")).isEqualTo("BLOCKED");
        assertThat(reply.body()).contains(blocker);
        assertThat(api.get("/api/inspections/" + inspection + "/eligibility").body()).contains(blocker);
    }

    @Test
    void theSameResultIsCertifiableInOneJurisdictionAndNotInAnother() {
        String buenosAires = inspectedAndPlanned("AR-BA", "untidy");
        String cordoba = inspectedAndPlanned("AR-CBA", "untidy");

        var allowed = api.post("/api/inspections/" + buenosAires + "/certificates", Map.of(), null);
        var refused = api.post("/api/inspections/" + cordoba + "/certificates", Map.of(), null);

        assertThat(allowed.status()).isEqualTo(201);
        assertThat(refused.status()).isEqualTo(422);
    }

    @Test
    void anAssetCannotBeRegisteredInAnUnknownJurisdiction() {
        var asset = new HashMap<String, Object>();
        asset.put("name", "Nowhere " + System.nanoTime());
        asset.put("assetType", "EQUIPMENT");
        asset.put("responsibleId", organization("Owner"));
        asset.put("location", "Somewhere");
        asset.put("characteristics", Map.of("room", "1"));
        asset.put("jurisdiction", "MARS");

        var reply = api.post("/api/assets", asset, null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("INVALID_INPUT");
        assertThat(reply.text("message")).contains("MARS", "AR-BA");
    }
}
