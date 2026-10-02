package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogueEdgeTest {

    @Test
    @DisplayName("a person can be used as inspector and an organization cannot")
    void onlyPersonsCanBeInspectors() {
        Party person = new Party(PartyId.of("person-1"), "Ana", PartyKind.PERSON);
        Party organization = new Party(PartyId.of("org-1"), "ACME", PartyKind.ORGANIZATION);

        assertThat(person.id()).isEqualTo(PartyId.of("person-1"));
        assertThat(person.name()).isEqualTo("Ana");
        assertThat(person.kind()).isEqualTo(PartyKind.PERSON);
        assertThat(person.asInspector()).isEqualTo(person.id());
        assertThat(person.reference()).isEqualTo(new ResponsiblePartyRef(person.id(), "Ana"));
        assertThat(person).isEqualTo(new Party(PartyId.of("person-1"), "Other", PartyKind.PERSON));
        assertThat(person).isNotEqualTo(organization);
        assertThat(person).isNotEqualTo("person-1");
        assertThat(person.hashCode()).isEqualTo(PartyId.of("person-1").hashCode());
        assertThat(person.toString()).isEqualTo("Ana (person-1)");
        assertThatThrownBy(organization::asInspector)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot be assigned as inspector");
    }

    @Test
    @DisplayName("assets reject unchanged responsible and location while exposing snapshots")
    void assetsRejectNoOpChangesAndExposeSnapshots() {
        ResponsiblePartyRef responsible = new ResponsiblePartyRef(PartyId.of("owner-1"), "Owner");
        Asset asset = new Asset(AssetId.of("asset-1"), "Freezer", AssetType.EQUIPMENT,
                Map.of("room", "12", "brand", "Acme"), responsible, "Building 1");

        assertThat(asset.id()).isEqualTo(AssetId.of("asset-1"));
        assertThat(asset.name()).isEqualTo("Freezer");
        assertThat(asset.assetType()).isEqualTo(AssetType.EQUIPMENT);
        assertThat(asset.characteristics()).containsEntry("brand", "Acme");
        assertThat(asset.responsible()).isEqualTo(responsible);
        assertThat(asset.location()).isEqualTo("Building 1");
        assertThat(asset.captureSnapshot(Instant.parse("2026-03-01T10:00:00Z")).assetId())
                .isEqualTo(asset.id());
        assertThat(asset).isEqualTo(new Asset(AssetId.of("asset-1"), "Other", AssetType.EQUIPMENT,
                Map.of("room", "14"), responsible, "Building 2"));
        assertThat(asset).isNotEqualTo(new Asset(AssetId.of("asset-2"), "Freezer", AssetType.EQUIPMENT,
                Map.of("room", "12"), responsible, "Building 1"));
        assertThat(asset).isNotEqualTo("asset-1");
        assertThat(asset.hashCode()).isEqualTo(AssetId.of("asset-1").hashCode());
        assertThat(asset.toString()).isEqualTo("Freezer (asset-1)");

        assertThatThrownBy(() -> asset.assignResponsible(responsible))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already the responsibility");
        assertThatThrownBy(() -> asset.relocate("Building 1"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already located");
    }

    @Test
    @DisplayName("asset types expose and enforce their predefined characteristics")
    void assetTypesValidateCharacteristics() {
        assertThat(AssetType.FACILITY.characteristicNames()).containsExactlyInAnyOrder("room", "purpose");
        assertThat(AssetType.LABORATORY.validateCharacteristics(Map.of("room", "1")))
                .containsEntry("room", "1");
        assertThatThrownBy(() -> AssetType.LABORATORY.validateCharacteristics(Map.of("brand", "Acme")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must be predefined");
    }
}
