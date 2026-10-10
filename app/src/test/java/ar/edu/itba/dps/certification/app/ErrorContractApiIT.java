package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The error contract as a client sees it: status, stable code and no internals. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-errors-it;DB_CLOSE_DELAY=-1")
class ErrorContractApiIT extends ApiTest {

    @Test
    void anUnknownRouteIs404NotAServerError() {
        var reply = api.get("/api/does-not-exist");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.text("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void aMethodTheRouteDoesNotOfferIs405() {
        var reply = api.delete("/api/parties/anyone");

        assertThat(reply.status()).isEqualTo(405);
        assertThat(reply.text("code")).isEqualTo("REQUEST_REFUSED");
    }

    @Test
    void aBodyThatIsNotJsonIs400() {
        var reply = api.postRaw("/api/parties", "{ this is not json");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void anEnumParameterWithAnUnknownValueIs400() {
        var reply = api.get("/api/inspections?status=BOGUS");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void anActorThatDoesNotExistIs401EvenOnAReadOnlyRoute() {
        var reply = api.getAs("/api/parties", "ghost");

        assertThat(reply.status()).isEqualTo(401);
        assertThat(reply.text("code")).isEqualTo("ACTOR_REQUIRED");
    }

    @Test
    void anOperationThatNeedsAnActorIs401WithoutTheHeader() {
        String inspector = person("Inspector");
        String owner = organization("Owner");
        var asset = new java.util.HashMap<String, Object>();
        asset.put("name", "Lab " + System.nanoTime());
        asset.put("assetType", "LABORATORY");
        asset.put("responsibleId", owner);
        asset.put("location", "Floor 1");
        asset.put("characteristics", Map.of("room", "1"));
        asset.put("jurisdiction", "REFERENCE");
        String assetId = api.post("/api/assets", asset, null).text("id");
        String inspection = api.post("/api/inspections", Map.of("assetId", assetId, "inspectorId", inspector,
                "expectedDate", "2027-01-15"), null).text("id");

        var reply = api.post("/api/inspections/" + inspection + "/start", Map.of(), null);

        assertThat(reply.status()).isEqualTo(401);
        assertThat(reply.text("code")).isEqualTo("ACTOR_REQUIRED");
    }

    @Test
    void aBlankNameIs400AndTheErrorBodyHasItsStandardFields() {
        var reply = api.post("/api/parties", Map.of("name", "   ", "kind", "PERSON"), null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("INVALID_INPUT");
        assertThat(reply.json()).containsKeys("status", "code", "message", "timestamp");
    }

    @Test
    void errorsNeverLeakStackTracesOrSql() {
        var reply = api.get("/api/inspections/does-not-exist");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.body()).doesNotContain("Exception", "at ar.edu", "SELECT", "jdbc");
    }
}
