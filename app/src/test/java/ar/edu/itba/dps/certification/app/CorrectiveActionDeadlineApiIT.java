package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-deadline-boundary-it;DB_CLOSE_DELAY=-1")
@Import(SteerableClock.Config.class)
class CorrectiveActionDeadlineApiIT extends ApiTest {

    @Autowired
    SteerableClock clock;

    @BeforeEach
    void setUp() {
        clock.reset();
    }

    @Test
    void configuredLocalDateGovernsPlanningEligibilityAndExpiryOverHttp() {
        clock.setNow(Instant.parse("2026-03-03T00:30:00Z")); // March 2 in Buenos Aires.
        publishFacilitySchemaOnce();
        var inspected = closedFacilityInspection("REFERENCE", "untidy", "clean", "clean");
        String findingId = (String) api.get("/api/findings?inspectionId=" + inspected.inspectionId())
                .jsonList().getFirst().get("id");

        var planned = api.post("/api/findings/" + findingId + "/plan", Map.of(
                "work", "tidy the switchboard",
                "executorId", person("Executor"),
                "dueDate", "2026-03-02"), inspected.owner());
        var eligibility = api.get("/api/inspections/" + inspected.inspectionId()
                + "/eligibility?subsystem=electrical%20installation");
        var sweep = api.post("/api/admin/jobs/run", Map.of(), null);

        assertThat(planned.status()).as(planned.body()).isEqualTo(200);
        assertThat(eligibility.status()).as(eligibility.body()).isEqualTo(200);
        assertThat(eligibility.body()).doesNotContain("OverdueOpenAction");
        assertThat(sweep.status()).as(sweep.body()).isEqualTo(200);
        assertThat(((Number) sweep.json().get("expiredActions")).intValue()).isZero();
        @SuppressWarnings("unchecked")
        var action = ((List<Map<String, Object>>) api.get("/api/findings/" + findingId)
                .json().get("correctiveActions")).getFirst();
        assertThat(action.get("status")).isEqualTo("PLANNED");
        assertThat(action.get("deadline")).isEqualTo(LocalDate.of(2026, 3, 2).toString());
        assertThat(action.get("deadlineBreached")).isEqualTo(false);
    }
}
