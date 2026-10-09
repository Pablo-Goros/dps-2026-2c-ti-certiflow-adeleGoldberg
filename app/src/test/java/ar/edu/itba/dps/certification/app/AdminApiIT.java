package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The operations on the background processes, over HTTP. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-admin-it;DB_CLOSE_DELAY=-1")
class AdminApiIT extends ApiTest {

    @Test
    void theSweepsCanBeRunOnDemandAndReportWhatTheyDid() {
        var reply = api.post("/api/admin/jobs/run", java.util.Map.of(), null);

        assertThat(reply.status()).as(reply.body()).isEqualTo(200);
        assertThat(reply.json()).containsKeys("expiredCertificates", "expiredActions", "deliveredEvents");
    }

    @Test
    void theOutboxCanBeListedByStatus() {
        assertThat(api.get("/api/admin/outbox").status()).isEqualTo(200);
        assertThat(api.get("/api/admin/outbox?status=PENDING").status()).isEqualTo(200);
        assertThat(api.get("/api/admin/outbox?status=DEAD").status()).isEqualTo(200);
    }

    @Test
    void anUnknownStatusIsABadRequest() {
        var reply = api.get("/api/admin/outbox?status=WHATEVER");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("INVALID_INPUT");
    }

    @Test
    void retryingWithNothingDeadChangesNothing() {
        var reply = api.post("/api/admin/outbox/retry-dead", java.util.Map.of(), null);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("revived")).isEqualTo(0);
    }
}
