package ar.edu.itba.dps.certification.app;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:scheduled-withdrawal-it;DB_CLOSE_DELAY=-1")
class ScheduledVersionWithdrawalApiIT extends ApiTest {
    @Test void scheduledVersionsCannotBeSilentlyReplacedAndAnAuditedWithdrawalAllowsAnUrgentFix() {
        String operator = person("Schema editor");
        String id = api.post("/api/schemas", Map.of("name", "Equipment", "assetTypes", List.of("EQUIPMENT")), operator).text("id");
        String path = "/api/schemas/" + id;
        api.post(path + "/draft", Map.of(), operator);
        api.post(path + "/draft/sections", Fixtures.housekeepingSection(), operator);
        assertThat(api.post(path + "/publish", Map.of(), operator).status()).isEqualTo(201);
        String future = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        api.post(path + "/draft", Map.of(), operator);
        assertThat(api.post(path + "/publish", Map.of("effectiveFrom", future), operator).status()).isEqualTo(201);
        api.post(path + "/draft", Map.of(), operator);
        assertThat(api.post(path + "/publish", Map.of("effectiveFrom", future), operator).status()).isEqualTo(422);
        assertThat(api.post(path + "/publish", Map.of(), operator).status()).isEqualTo(422);
        assertThat(api.post(path + "/versions/1/withdraw", Map.of("reason", "Too late"), operator).status()).isEqualTo(422);
        assertThat(api.post(path + "/versions/2/withdraw", Map.of("reason", " "), operator).status()).isEqualTo(400);

        var withdrawn = api.post(path + "/versions/2/withdraw", Map.of("reason", "Wrong threshold"), operator);
        assertThat(withdrawn.status()).as(withdrawn.body()).isEqualTo(200);
        assertThat((List<?>) withdrawn.json().get("withdrawals")).hasSize(1);
        assertThat(api.get(path + "/effective-version?at=" + future).json().get("number")).isEqualTo(1);
        assertThat(api.get(path + "/versions/2").status()).isEqualTo(200);
        var urgent = api.post(path + "/publish", Map.of(), operator);
        assertThat(urgent.status()).as(urgent.body()).isEqualTo(201);
        assertThat(urgent.json().get("number")).isEqualTo(3);
        assertThat(api.get(path).json().get("effectiveVersion")).isEqualTo(3);
        assertThat((List<?>) api.get(path).json().get("versions")).hasSize(3);
        var audit = api.get("/api/audit?type=SCHEMA&id=" + id);
        assertThat(audit.body()).contains("SCHEMA_VERSION_WITHDRAWN", "Wrong threshold", operator);
    }
}
