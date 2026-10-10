package ar.edu.itba.dps.certification.app;

import ar.edu.itba.dps.certification.application.notification.Notification;
import ar.edu.itba.dps.certification.application.notification.NotificationFailedException;
import ar.edu.itba.dps.certification.application.notification.port.NotificationSender;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventHandler;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionPlanned;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The background processes judged by their effect, over HTTP: time is moved with a steerable
 * clock and the sweeps are run on demand, then the certificates, the findings, the notifications
 * and the outbox are looked at. Nothing is mocked: the clock, the notification channel and one
 * event handler are test doubles plugged into the real application.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:certiflow-processes-it;DB_CLOSE_DELAY=-1",
            "certiflow.outbox.max-attempts=2"
        })
@Import({SteerableClock.Config.class, ProcessesApiIT.Doubles.class})
class ProcessesApiIT extends ApiTest {

    /** Remembers what it was asked to send; can be told to fail like a channel that is down. */
    static final class RecordingSender implements NotificationSender {
        final List<Notification> sent = new CopyOnWriteArrayList<>();
        volatile boolean down;

        @Override
        public void send(Notification notification) {
            if (down) {
                throw new NotificationFailedException("channel is down");
            }
            sent.add(notification);
        }

        List<Notification> sentTo(String recipientId) {
            return sent.stream().filter(n -> n.recipientId().equals(recipientId)).toList();
        }
    }

    /** A handler that blows up on {@code CorrectiveActionPlanned} while switched on. */
    static final class FlakyHandler implements DomainEventHandler {
        volatile boolean failing;

        @Override
        public void handle(DomainEvent event) {
            if (failing && event instanceof CorrectiveActionPlanned) {
                throw new IllegalStateException("boom");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Doubles {

        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }

        @Bean
        FlakyHandler flakyHandler() {
            return new FlakyHandler();
        }
    }

    @Autowired SteerableClock clock;
    @Autowired RecordingSender sender;
    @Autowired FlakyHandler flaky;

    @BeforeEach
    void freshWorld() {
        clock.reset();
        sender.down = false;
        flaky.failing = false;
        publishFacilitySchemaOnce();
    }

    @AfterEach
    void putEverythingBack() {
        flaky.failing = false;
        sender.down = false;
        api.post("/api/admin/outbox/retry-dead", Map.of(), null);
    }

    /** A conditional certificate for the electrical subsystem, backed by a planned (not yet done) correction. */
    private record Backed(Inspected inspected, String certificateId, String findingId) {
    }

    private Backed conditionalElectricalCertificate() {
        var inspected = closedFacilityInspection("REFERENCE", "untidy", "clean", "clean");
        String path = "/api/inspections/" + inspected.inspectionId();
        String findingId = (String) api.get("/api/findings?inspectionId=" + inspected.inspectionId())
                .jsonList().get(0).get("id");
        var planned = api.post("/api/findings/" + findingId + "/plan", Map.of("work", "tidy up",
                "executorId", person("Executor"), "dueDate", clock.today().plusDays(10).toString()),
                inspected.owner());
        assertThat(planned.status()).as(planned.body()).isEqualTo(200);
        var issued = api.post(path + "/certificates", Map.of("subsystem", "electrical installation"), null);
        assertThat(issued.status()).as(issued.body()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        var certificate = (Map<String, Object>) issued.json().get("certificate");
        assertThat(certificate.get("mode")).isEqualTo("CONDITIONAL");
        return new Backed(inspected, (String) certificate.get("id"), findingId);
    }

    private String certificateStatus(String id) {
        return api.get("/api/certificates/" + id).text("status");
    }

    private List<Map<String, Object>> outbox(String status) {
        return api.get("/api/admin/outbox?status=" + status).jsonListOrEmpty();
    }

    private static boolean isAbout(Map<String, Object> entry, String eventType) {
        return String.valueOf(entry.get("eventType")).contains(eventType);
    }

    @Test
    void anOverdueCorrectiveActionSuspendsItsCertificateAndTellsTheResponsible() {
        var backed = conditionalElectricalCertificate();
        assertThat(certificateStatus(backed.certificateId())).isEqualTo("VALID");

        clock.advanceDays(30);
        var run = api.post("/api/admin/jobs/run", Map.of(), null);

        assertThat(run.status()).as(run.body()).isEqualTo(200);
        assertThat(((Number) run.json().get("expiredActions")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(certificateStatus(backed.certificateId())).isEqualTo("SUSPENDED");
        @SuppressWarnings("unchecked")
        var action = ((List<Map<String, Object>>) api.get("/api/findings/" + backed.findingId())
                .json().get("correctiveActions")).get(0);
        assertThat(action.get("deadlineBreached")).isEqualTo(true);
        assertThat(sender.sentTo(backed.inspected().owner())).singleElement().satisfies(notification -> {
            assertThat(notification.subject()).isEqualTo("Corrective action overdue");
            assertThat(notification.message()).contains(backed.findingId());
        });
    }

    @Test
    void runningTheSweepsAgainDoesNotRepeatTheEffects() {
        var backed = conditionalElectricalCertificate();
        clock.advanceDays(30);
        api.post("/api/admin/jobs/run", Map.of(), null);

        var second = api.post("/api/admin/jobs/run", Map.of(), null);

        assertThat(second.status()).isEqualTo(200);
        assertThat(certificateStatus(backed.certificateId())).isEqualTo("SUSPENDED");
        assertThat(sender.sentTo(backed.inspected().owner())).hasSize(1);
    }

    @Test
    void aNotificationChannelThatIsDownDoesNotBlockTheSuspension() {
        sender.down = true;
        var backed = conditionalElectricalCertificate();

        clock.advanceDays(30);
        api.post("/api/admin/jobs/run", Map.of(), null);

        assertThat(certificateStatus(backed.certificateId())).isEqualTo("SUSPENDED");
        assertThat(sender.sentTo(backed.inspected().owner())).isEmpty();
        assertThat(outbox("DEAD")).noneMatch(entry -> isAbout(entry, "CorrectiveActionExpired"));
        assertThat(outbox("PENDING")).noneMatch(entry -> isAbout(entry, "CorrectiveActionExpired"));
    }

    @Test
    void aCertificateWhoseValidityEndedIsExpiredBySweep() {
        publishFacilitySchemaOnce();
        var inspected = closedFacilityInspection("REFERENCE", "clean", "clean", "clean");
        var issued = api.post("/api/inspections/" + inspected.inspectionId() + "/certificates",
                Map.of("subsystem", "building safety"), null);
        assertThat(issued.status()).as(issued.body()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        String certificateId = (String) ((Map<String, Object>) issued.json().get("certificate")).get("id");
        assertThat(certificateStatus(certificateId)).isEqualTo("VALID");

        clock.advanceDays(400);
        var run = api.post("/api/admin/jobs/run", Map.of(), null);

        assertThat(((Number) run.json().get("expiredCertificates")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(certificateStatus(certificateId)).isEqualTo("EXPIRED");
    }

    @Test
    void anEventWhoseHandlerKeepsFailingIsRetriedThenGivesUpAndCanBeRevived() {
        flaky.failing = true;
        var inspected = closedFacilityInspection("REFERENCE", "untidy", "clean", "clean");
        String findingId = (String) api.get("/api/findings?inspectionId=" + inspected.inspectionId())
                .jsonList().get(0).get("id");

        var planned = api.post("/api/findings/" + findingId + "/plan", Map.of("work", "tidy up",
                "executorId", person("Executor"), "dueDate", clock.today().plusDays(10).toString()),
                inspected.owner());

        assertThat(planned.status()).as("a failing handler never fails the request").isEqualTo(200);
        var waiting = outbox("PENDING").stream().filter(e -> isAbout(e, "CorrectiveActionPlanned")).toList();
        assertThat(waiting).singleElement().satisfies(entry -> {
            assertThat(((Number) entry.get("attempts")).intValue()).isEqualTo(1);
            assertThat(String.valueOf(entry.get("lastError"))).contains("boom");
        });
        Object seq = waiting.get(0).get("seq");

        api.post("/api/admin/jobs/run", Map.of(), null);
        assertThat(outbox("DEAD")).anyMatch(entry -> seq.equals(entry.get("seq")));
        assertThat(outbox("PENDING")).noneMatch(entry -> seq.equals(entry.get("seq")));

        flaky.failing = false;
        var revived = api.post("/api/admin/outbox/retry-dead", Map.of(), null);

        assertThat(((Number) revived.json().get("revived")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(outbox("DEAD")).noneMatch(entry -> seq.equals(entry.get("seq")));
        assertThat(outbox("DONE")).anyMatch(entry -> seq.equals(entry.get("seq")));
    }
}
