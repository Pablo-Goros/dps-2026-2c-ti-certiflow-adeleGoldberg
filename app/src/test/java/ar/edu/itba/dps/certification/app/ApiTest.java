package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base of the API tests: the whole application on a random port over an in-memory H2 database
 * (the same adapters and migrations as production, nothing mocked).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:certiflow-api;DB_CLOSE_DELAY=-1"
        })
abstract class ApiTest {

    @Value("${local.server.port}")
    int port;

    Api api;

    @BeforeEach
    void connect() {
        api = new Api(port);
    }

    /** Registers a person and returns the new id. Names are unique per call so tests can share the database. */
    String person(String name) {
        var reply = api.post("/api/parties", java.util.Map.of("name", name + " " + System.nanoTime(),
                "kind", "PERSON"), null);
        return reply.text("id");
    }

    String organization(String name) {
        var reply = api.post("/api/parties", java.util.Map.of("name", name + " " + System.nanoTime(),
                "kind", "ORGANIZATION"), null);
        return reply.text("id");
    }
}
