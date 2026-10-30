package ar.edu.itba.dps.certification.app;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** With the default configuration the assistant is off, and the application starts without it. */
class AssistantDisabledApiIT extends ApiTest {

    @Test
    void theAssistantHasNoEndpointsUntilItIsEnabled() {
        var consulted = api.post("/api/inspections/any/consultations", Map.of(), person("Inspector"));

        // An enabled controller would also answer 404 here, but naming the missing inspection.
        assertThat(consulted.status()).isEqualTo(404);
        assertThat(consulted.text("message")).isEqualTo("there is nothing at this address");
    }
}
