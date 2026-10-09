package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PartyApiIT extends ApiTest {

    @Test
    void registeringAPartyReturnsItWithItsLocation() {
        var reply = api.post("/api/parties", Map.of("name", "Ana Pérez", "kind", "PERSON"), null);

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.text("name")).isEqualTo("Ana Pérez");
        assertThat(reply.text("kind")).isEqualTo("PERSON");
        assertThat(reply.location()).isEqualTo("/api/parties/" + reply.text("id"));
    }

    @Test
    void aRegisteredPartyCanBeReadBackAndListed() {
        String id = person("Lucía");

        var one = api.get("/api/parties/" + id);
        var all = api.get("/api/parties");

        assertThat(one.status()).isEqualTo(200);
        assertThat(one.text("id")).isEqualTo(id);
        assertThat(all.jsonList()).extracting(party -> party.get("id")).contains(id);
    }

    @Test
    void anUnknownPartyIsNotFound() {
        var reply = api.get("/api/parties/does-not-exist");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.text("code")).isEqualTo("NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankNameIsABadRequest(String name) {
        var reply = api.post("/api/parties", Map.of("name", name, "kind", "PERSON"), null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("INVALID_INPUT");
    }

    @Test
    void anUnknownKindIsAMalformedRequest() {
        var reply = api.post("/api/parties", Map.of("name", "Ana", "kind", "ROBOT"), null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void brokenJsonIsAMalformedRequest() {
        var reply = api.postRaw("/api/parties", "{not json");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.text("code")).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void anUnknownActorIsRejectedBeforeAnythingRuns() {
        var reply = api.getAs("/api/parties", "nobody");

        assertThat(reply.status()).isEqualTo(401);
        assertThat(reply.text("code")).isEqualTo("ACTOR_REQUIRED");
    }
}
