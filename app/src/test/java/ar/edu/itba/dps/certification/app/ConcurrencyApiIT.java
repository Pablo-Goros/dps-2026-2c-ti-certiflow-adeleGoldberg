package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simultaneous requests against the real database: the invariants hold and the losers get a
 * conflict or a business-rule answer, never a server error.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-concurrency-it;DB_CLOSE_DELAY=-1")
class ConcurrencyApiIT extends ApiTest {

    private static final int CALLERS = 6;

    /** Fires the same call from several threads at once and returns every reply. */
    private static List<Api.Reply> simultaneously(int callers, Supplier<Api.Reply> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CountDownLatch ready = new CountDownLatch(callers);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Api.Reply>> pending = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.get();
                }));
            }
            ready.await();
            go.countDown();
            List<Api.Reply> replies = new ArrayList<>();
            for (Future<Api.Reply> future : pending) {
                try {
                    replies.add(future.get());
                } catch (ExecutionException e) {
                    throw new IllegalStateException(e.getCause());
                }
            }
            return replies;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ofSeveralSimultaneousSchemasForTheSameAssetTypeOnlyOneIsCreated() throws Exception {
        var replies = simultaneously(CALLERS, () -> api.post("/api/schemas",
                Map.of("name", "Factory", "assetTypes", List.of("FACTORY")), null));

        assertThat(replies.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        assertThat(replies).extracting(Api.Reply::status).allMatch(status -> status == 201 || status == 409 || status == 422);
        var factorySchemas = api.get("/api/schemas").jsonList().stream()
                .filter(schema -> ((List<?>) schema.get("assetTypes")).contains("FACTORY")).toList();
        assertThat(factorySchemas).hasSize(1);
    }

    @Test
    void ofSeveralSimultaneousIssuancesForTheSameInspectionOnlyOneCertificateExists() throws Exception {
        String schemaId = api.post("/api/schemas", Map.of("name", "Equipment", "assetTypes", List.of("EQUIPMENT")),
                null).text("id");
        api.post("/api/schemas/" + schemaId + "/draft", Map.of(), null);
        api.post("/api/schemas/" + schemaId + "/draft/sections", Fixtures.housekeepingSection(), null);
        assertThat(api.post("/api/schemas/" + schemaId + "/publish", Map.of(), null).status()).isEqualTo(201);
        String owner = organization("Owner");
        String inspector = person("Inspector");
        var asset = new HashMap<String, Object>();
        asset.put("name", "Press " + System.nanoTime());
        asset.put("assetType", "EQUIPMENT");
        asset.put("responsibleId", owner);
        asset.put("location", "Workshop");
        asset.put("characteristics", Map.of("room", "1"));
        asset.put("jurisdiction", "REFERENCE");
        String assetId = api.post("/api/assets", asset, null).text("id");
        String inspection = api.post("/api/inspections", Map.of("assetId", assetId, "inspectorId", inspector,
                "expectedDate", "2027-01-15"), null).text("id");
        api.post("/api/inspections/" + inspection + "/start", Map.of(), inspector);
        api.put("/api/inspections/" + inspection + "/answers/HK", Fixtures.option("clean"), inspector);
        assertThat(api.post("/api/inspections/" + inspection + "/close", Map.of(), inspector).status()).isEqualTo(200);

        var replies = simultaneously(CALLERS,
                () -> api.post("/api/inspections/" + inspection + "/certificates", Map.of(), null));

        assertThat(replies).extracting(Api.Reply::status)
                .allMatch(status -> status == 201 || status == 200 || status == 409 || status == 422);
        assertThat(replies.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        assertThat(api.get("/api/certificates?assetId=" + assetId).jsonList()).hasSize(1);
    }
}
