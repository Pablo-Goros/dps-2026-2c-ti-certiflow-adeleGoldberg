package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-schema-draft-editing-it;DB_CLOSE_DELAY=-1")
class SchemaDraftEditingApiIT extends ApiTest {

    @Test
    void draftSectionsCanBeRemovedAndTheDraftCanBeDiscarded() {
        var created = api.post("/api/schemas",
                Map.of("name", "Draft editing", "assetTypes", List.of("EQUIPMENT")), null);
        String id = created.text("id");

        var opened = api.post("/api/schemas/" + id + "/draft", Map.of(), null);
        var added = api.post("/api/schemas/" + id + "/draft/sections", Fixtures.housekeepingSection(), null);
        var removed = api.delete("/api/schemas/" + id + "/draft/sections/Housekeeping");
        var discarded = api.delete("/api/schemas/" + id + "/draft");
        var reread = api.get("/api/schemas/" + id);

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(opened.status()).isEqualTo(200);
        assertThat(added.status()).as(added.body()).isEqualTo(200);
        assertThat(removed.status()).as(removed.body()).isEqualTo(200);
        assertThat(discarded.status()).as(discarded.body()).isEqualTo(200);
        assertThat(reread.json().get("draft")).isNull();
    }
}
