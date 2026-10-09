package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssetApiIT extends ApiTest {

    private Map<String, Object> facility(String name, String responsibleId) {
        return Map.of("name", name, "assetType", "FACILITY", "responsibleId", responsibleId,
                "location", "Building 1", "characteristics", Map.of("room", "12"),
                "jurisdiction", "AR-BA");
    }

    @Test
    void registeringAFacilityDeclaresAllTheSubsystemsOfItsType() {
        String owner = organization("Planta");

        var reply = api.post("/api/assets", facility("Planta Norte " + System.nanoTime(), owner), null);

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.json().get("subsystems"))
                .isEqualTo(List.of("electrical installation", "pressure system", "building safety"));
        assertThat(reply.text("jurisdiction")).isEqualTo("AR-BA");
        assertThat(reply.location()).isEqualTo("/api/assets/" + reply.text("id"));
    }

    @Test
    void anAssetCanBeDeclaredWithOnlySomeSubsystems() {
        String owner = organization("Planta");
        var body = new java.util.HashMap<>(facility("Parcial " + System.nanoTime(), owner));
        body.put("subsystems", List.of("electrical installation"));

        var reply = api.post("/api/assets", body, null);

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.json().get("subsystems")).isEqualTo(List.of("electrical installation"));
    }

    @Test
    void aSubsystemOfAnotherTypeIsABusinessError() {
        String owner = organization("Planta");
        var body = new java.util.HashMap<String, Object>();
        body.put("name", "Lab " + System.nanoTime());
        body.put("assetType", "LABORATORY");
        body.put("responsibleId", owner);
        body.put("location", "Piso 2");
        body.put("characteristics", Map.of("room", "3"));
        body.put("jurisdiction", "REFERENCE");
        body.put("subsystems", List.of("pressure system"));

        var reply = api.post("/api/assets", body, null);

        assertThat(reply.status()).isBetween(400, 422);
        assertThat(reply.text("code")).isIn("INVALID_INPUT", "BUSINESS_RULE");
    }

    @Test
    void aResponsibleThatDoesNotExistIsRefused() {
        var reply = api.post("/api/assets", facility("Huérfano " + System.nanoTime(), "ghost"), null);

        assertThat(reply.status()).isEqualTo(422);
        assertThat(reply.text("code")).isEqualTo("BUSINESS_RULE");
    }

    @Test
    void unknownCharacteristicsAreRefused() {
        String owner = organization("Planta");
        var body = new java.util.HashMap<>(facility("Raro " + System.nanoTime(), owner));
        body.put("characteristics", Map.of("colour", "red"));

        var reply = api.post("/api/assets", body, null);

        assertThat(reply.status()).isBetween(400, 422);
    }

    @Test
    void assetsCanBeFilteredByTypeAndByName() {
        String owner = organization("Planta");
        String unique = "Filtrable" + System.nanoTime();
        api.post("/api/assets", facility(unique, owner), null);

        var byName = api.get("/api/assets?name=" + unique);
        var byType = api.get("/api/assets?type=FACILITY");
        var byBadType = api.get("/api/assets?type=SPACESHIP");

        assertThat(byName.jsonList()).hasSize(1);
        assertThat(byType.jsonList()).extracting(asset -> asset.get("name")).contains(unique);
        assertThat(byBadType.status()).isEqualTo(400);
    }

    @Test
    void relocatingAndChangingTheResponsibleAreVisibleOnTheNextRead() {
        String owner = organization("Planta");
        String newOwner = organization("Otra planta");
        String id = api.post("/api/assets", facility("Movil " + System.nanoTime(), owner), null).text("id");

        var moved = api.put("/api/assets/" + id + "/location", Map.of("location", "Building 9"), null);
        var transferred = api.put("/api/assets/" + id + "/responsible", Map.of("responsibleId", newOwner), null);
        var reread = api.get("/api/assets/" + id);

        assertThat(moved.status()).isEqualTo(200);
        assertThat(transferred.status()).isEqualTo(200);
        assertThat(reread.text("location")).isEqualTo("Building 9");
        assertThat(reread.text("responsibleId")).isEqualTo(newOwner);
    }

    @Test
    void changingAnUnknownAssetIsNotFound() {
        var reply = api.put("/api/assets/nope/location", Map.of("location", "x"), null);

        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void referenceDataDescribesTheAssetTypesAndJurisdictions() {
        var types = api.get("/api/meta/asset-types");
        var jurisdictions = api.get("/api/meta/jurisdictions");

        assertThat(types.status()).isEqualTo(200);
        assertThat(types.jsonList()).extracting(type -> type.get("name"))
                .containsExactlyInAnyOrder("LABORATORY", "FACTORY", "FACILITY", "EQUIPMENT");
        assertThat(jurisdictions.body()).contains("AR-BA", "REFERENCE");
    }
}
