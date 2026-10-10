package ar.edu.itba.dps.certification.app;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbound integration end to end: an overdue corrective action reaches a real HTTP endpoint
 * configured through {@code certiflow.notifications.webhook-url}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-webhook-it;DB_CLOSE_DELAY=-1")
@Import(SteerableClock.Config.class)
class NotificationWebhookApiIT extends ApiTest {

    private static final List<String> POSTED = new CopyOnWriteArrayList<>();
    private static final HttpServer HOOK = start();

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/hook", exchange -> {
                POSTED.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void webhook(DynamicPropertyRegistry registry) {
        registry.add("certiflow.notifications.webhook-url",
                () -> "http://127.0.0.1:" + HOOK.getAddress().getPort() + "/hook");
    }

    @AfterAll
    static void stopTheEndpoint() {
        HOOK.stop(0);
    }

    @Autowired SteerableClock clock;

    @BeforeEach
    void prepare() {
        clock.reset();
        publishFacilitySchemaOnce();
    }

    @Test
    void anOverdueCorrectiveActionIsPostedToTheConfiguredEndpoint() {
        var inspected = closedFacilityInspection("REFERENCE", "untidy", "clean", "clean");
        String findingId = (String) api.get("/api/findings?inspectionId=" + inspected.inspectionId())
                .jsonList().get(0).get("id");
        var planned = api.post("/api/findings/" + findingId + "/plan", Map.of("work", "tidy up",
                "executorId", person("Executor"), "dueDate", clock.today().plusDays(10).toString()),
                inspected.owner());
        assertThat(planned.status()).as(planned.body()).isEqualTo(200);
        assertThat(POSTED).noneMatch(body -> body.contains(inspected.owner()));

        clock.advanceDays(30);
        api.post("/api/admin/jobs/run", Map.of(), null);

        assertThat(POSTED).filteredOn(body -> body.contains(inspected.owner())).singleElement().satisfies(body -> {
            assertThat(body).contains("\"subject\":\"Corrective action overdue\"");
            assertThat(body).contains(findingId);
        });
    }
}
